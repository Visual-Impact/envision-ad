package com.envisionad.webservice.business.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.business.exceptions.*;
import com.envisionad.webservice.business.mappinglayer.BusinessMapper;
import com.envisionad.webservice.business.mappinglayer.EmployeeMapper;
import com.envisionad.webservice.business.mappinglayer.InvitationMapper;
import com.envisionad.webservice.business.mappinglayer.VerificationMapper;
import com.envisionad.webservice.business.presentationlayer.models.*;
import com.envisionad.webservice.business.utils.Validator;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.utils.EmailService;
import com.envisionad.webservice.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BusinessServiceImpl implements BusinessService {

    @Value("${app.base.url}")
    private String appBaseUrl;

    private final EmailService emailService;

    private final BusinessRepository businessRepository;

    private final InvitationRepository invitationRepository;

    private final EmployeeRepository employeeRepository;

    private final VerificationRepository verificationRepository;

    private final BusinessMapper businessMapper;

    private final EmployeeMapper employeeMapper;

    private final InvitationMapper invitationMapper;

    private final VerificationMapper verificationMapper;

    private final JwtUtils jwtUtils;

    private final Auth0Service auth0Service;

    public BusinessServiceImpl(EmailService emailService, BusinessRepository businessRepository, InvitationRepository invitationRepository, EmployeeRepository employeeRepository, VerificationRepository verificationRepository, BusinessMapper businessMapper, EmployeeMapper employeeMapper, InvitationMapper invitationMapper, VerificationMapper verificationMapper, JwtUtils jwtUtils, Auth0Service auth0Service) {
        this.emailService = emailService;
        this.businessRepository = businessRepository;
        this.invitationRepository = invitationRepository;
        this.employeeRepository = employeeRepository;
        this.verificationRepository = verificationRepository;
        this.businessMapper = businessMapper;
        this.employeeMapper = employeeMapper;
        this.invitationMapper = invitationMapper;
        this.verificationMapper = verificationMapper;
        this.jwtUtils = jwtUtils;
        this.auth0Service = auth0Service;
    }

    @Override
    public BusinessResponseModel createBusiness(Jwt jwt, BusinessRequestModel businessRequestModel) {
        Validator.validateBusiness(businessRequestModel);

        if (businessRepository.existsByNameAndBusinessId_BusinessIdNot(businessRequestModel.getName(), null))
            throw new DuplicateBusinessNameException();

        String userId = jwtUtils.extractUserId(jwt);
        if(employeeRepository.existsByUserId(userId))
            throw new AccessDeniedException("Access denied");

        Business business = businessMapper.toEntity(businessRequestModel);
        business.setBusinessId(new BusinessIdentifier());
        business.setOwnerId(userId);

        Employee employee = new Employee();
        employee.setBusinessId(business.getBusinessId());
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setUserId(userId);

        businessRepository.save(business);
        employeeRepository.save(employee);

        return businessMapper.toResponse(business);
    }

    @Override
    public List<BusinessResponseModel> getAllBusinesses() {
        return businessRepository.findAll().stream().map(businessMapper::toResponse).toList();
    }

    @Override
    public BusinessResponseModel getBusinessById(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        return businessMapper.toResponse(business);
    }

    @Override
    public BusinessResponseModel updateBusinessById(Jwt jwt, String businessId, BusinessRequestModel businessRequestModel) {
        Validator.validateBusiness(businessRequestModel);

        Business existingBusiness = businessRepository.findByBusinessId_BusinessId(businessId);
        if (existingBusiness == null)
            throw new BusinessNotFoundException(businessId);

        if (businessRepository.existsByNameAndBusinessId_BusinessIdNot(businessRequestModel.getName(), businessId))
            throw new DuplicateBusinessNameException();

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        Business newBusiness = businessMapper.toEntity(businessRequestModel);
        newBusiness.setId(existingBusiness.getId());
        newBusiness.setBusinessId(existingBusiness.getBusinessId());
        newBusiness.setOwnerId(existingBusiness.getOwnerId());

        return businessMapper.toResponse(businessRepository.save(newBusiness));
    }

    @Override
    public VerificationResponseModel approveBusinessVerification(String businessId, String verificationId){
        VerificationContext context = updateBusinessVerification(businessId, verificationId);

        context.verification.setStatus(VerificationStatus.APPROVED);
        context.business.setVerified(true);

        businessRepository.save(context.business);
        return verificationMapper.toResponse(verificationRepository.save(context.verification));
    }

    @Override
    public VerificationResponseModel denyBusinessVerification(String businessId, String verificationId, String reason) {
        if (reason == null || reason.isEmpty() || reason.length() > 512)
            throw new BadVerificationRequestException();

        Verification verification = updateBusinessVerification(businessId, verificationId).verification;

        verification.setStatus(VerificationStatus.DENIED);
        verification.setComments(reason);

        return verificationMapper.toResponse(verificationRepository.save(verification));
    }

    @Override
    public VerificationResponseModel requestVerification(Jwt jwt, String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        if (business.isVerified())
            throw new BusinessAlreadyVerifiedException();

        if (verificationRepository.findAllByBusinessId_BusinessId(businessId).stream().anyMatch(v -> v.getStatus() == VerificationStatus.PENDING))
            throw new BadVerificationRequestException();

        Verification verification = new Verification();
        verification.setVerificationId(new VerificationIdentifier());
        verification.setBusinessId(new BusinessIdentifier(businessId));

        return verificationMapper.toResponse(verificationRepository.save(verification));
    }

    @Override
    public List<VerificationResponseModel> getAllVerificationsByBusinessId(Jwt jwt, String businessId) {
        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        return verificationRepository.findAllByBusinessId_BusinessId(businessId).stream().map(verificationMapper::toResponse).toList();
    }

    @Override
    public List<VerificationResponseModel> getAllVerificationRequests() {
        return verificationRepository.findAllByStatus(VerificationStatus.PENDING).stream().map(verificationMapper::toResponse).toList();
    }

    @Override
    public InvitationResponseModel createInvitation(Jwt jwt, String businessId, InvitationRequestModel invitationRequest) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        Invitation invitation =  invitationMapper.toEntity(invitationRequest);

        if (invitationRepository.existsByBusinessId_BusinessIdAndEmail(businessId, invitation.getEmail()))
            throw new BadInvitationRequestException();

        String token = UUID.randomUUID().toString();
        invitation.setToken(token);
        invitation.setInvitationId(new InvitationIdentifier());
        invitation.setBusinessId(new BusinessIdentifier(businessId));
        invitation.setTimeExpires(LocalDateTime.now().plusHours(1));

        String link = appBaseUrl + "/invite?businessId=" + businessId + "&token=" + token;
        String subject = "Invitation to join " + business.getName() + " on Envision Ad";
        String body = "Hi there,\n\n"
                + "You've been invited to join " + business.getName() + " on Envision Ad, "
                + "a platform connecting digital signage screens with advertisers.\n\n"
                + "Click the link below to accept:\n"
                + link + "\n\n"
                + "This invitation link expires in 1 hour.\n\n"
                + "— The Envision Ad Team";
        emailService.sendSimpleEmail(invitation.getEmail(), subject, body);

        return invitationMapper.toResponse(invitationRepository.save(invitation));
    }

    @Override
    public void cancelInvitation(Jwt jwt, String businessId, String invitationId) {
        if (!businessRepository.existsByBusinessId_BusinessId(businessId))
            throw new BusinessNotFoundException(businessId);

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        Invitation invitation = invitationRepository.findByInvitationId_InvitationId(invitationId);

        if (invitation == null)
            throw new InvitationNotFoundException();

        invitationRepository.delete(invitation);
    }

    @Override
    public List<EmployeeResponseModel> getAllEmployeesByBusinessId(Jwt jwt, String businessId) {
        if (!businessRepository.existsByBusinessId_BusinessId(businessId))
            throw new BusinessNotFoundException(businessId);

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        return employeeRepository.findAllByBusinessId_BusinessId(businessId)
                .stream()
                .map(employeeMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<InvitationResponseModel> getAllInvitationsByBusinessId(Jwt jwt, String businessId) {
        if (!businessRepository.existsByBusinessId_BusinessId(businessId))
            throw new BusinessNotFoundException(businessId);

        List<Employee> employees = employeeRepository.findAllByBusinessId_BusinessId(businessId);

        String userId = jwtUtils.extractUserId(jwt);
        if (employees.stream().noneMatch(e -> e.getUserId().equals(userId)))
            throw new AccessDeniedException("Access Denied");


        return invitationRepository.findAllByBusinessId_BusinessId(businessId).stream().map(invitationMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public InvitationAcceptResponseModel addBusinessEmployee(Jwt jwt, String businessId, String token) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        Invitation invitation = invitationRepository.findByToken(token);
        if (invitation == null)
            throw new InvitationNotFoundException();

        // Checked before deleting (unlike the old unconditional delete-then-check) so an
        // still-pending invitation survives a LOGIN_REQUIRED round-trip below — see D-note
        // in P5-PROGRESS.md. An actually-expired one is still consumed here, same net
        // effect as before.
        if (invitation.getTimeExpires().isBefore(LocalDateTime.now())) {
            invitationRepository.delete(invitation);
            throw new InvitationNotFoundException();
        }

        InvitationAcceptResponseModel result = new InvitationAcceptResponseModel();

        if (jwt != null) {
            // Existing authenticated path (brief FR 3.2: "unchanged") — an invitee who
            // already had an Auth0 account, or who just came back from /auth/login after
            // a LOGIN_REQUIRED response below, lands here.
            invitationRepository.delete(invitation);

            String userId = jwtUtils.extractUserId(jwt);
            if (employeeRepository.existsByUserIdAndBusinessId_BusinessId(userId, businessId))
                throw new AccessDeniedException("User is already an employee");

            Employee employee = new Employee();
            employee.setBusinessId(new BusinessIdentifier(businessId));
            employee.setEmployeeId(new EmployeeIdentifier());
            employee.setUserId(userId);

            result.setStatus(InvitationAcceptStatus.ACCEPTED);
            result.setEmployee(employeeMapper.toResponse(employeeRepository.save(employee)));
            return result;
        }

        // No JWT. Never create the employee row from an email match alone — that would
        // let anyone accept on behalf of an existing Auth0 account without proving they
        // own it. Force a real login instead; the invitation is left intact so the
        // authenticated re-call above can still find it.
        if (auth0Service.findUserIdByEmail(invitation.getEmail()).isPresent()) {
            result.setStatus(InvitationAcceptStatus.LOGIN_REQUIRED);
            return result;
        }

        // Genuinely new invitee — provision the Auth0 user and complete the accept in
        // one step, same as the admin-created-account flow (AdminAccountServiceImpl).
        invitationRepository.delete(invitation);
        String invitedName = (invitation.getName() != null && !invitation.getName().isBlank())
                ? invitation.getName() : invitation.getEmail();
        String userId = auth0Service.createUser(invitation.getEmail(), invitedName);

        Employee employee = new Employee();
        employee.setBusinessId(new BusinessIdentifier(businessId));
        employee.setEmployeeId(new EmployeeIdentifier());
        employee.setUserId(userId);
        EmployeeResponseModel employeeResponse = employeeMapper.toResponse(employeeRepository.save(employee));

        // Best-effort, same posture as AdminAccountService (brief FR 4.2.d) — the
        // employee row already exists; a ticket/email hiccup here means the invitee
        // needs a fresh link, not that the whole accept should fail.
        try {
            String ticketUrl = auth0Service.createPasswordChangeTicket(userId, appBaseUrl + "/auth/login");
            String subject = "Your Envision Ad account is ready";
            String body = "Hi " + invitedName + ",\n\n"
                    + "You've joined " + business.getName() + " on Envision Ad.\n\n"
                    + "Set your password to get started:\n" + ticketUrl + "\n\n"
                    + "Once set, log in here:\n" + appBaseUrl + "/auth/login\n\n"
                    + "— The Envision Ad Team";
            emailService.sendSimpleEmail(invitation.getEmail(), subject, body);
        } catch (RuntimeException e) {
            log.warn("Post-provisioning ticket/email failed for new invitee {} joining business {}",
                    invitation.getEmail(), businessId, e);
        }

        result.setStatus(InvitationAcceptStatus.PROVISIONED);
        result.setEmployee(employeeResponse);
        return result;
    }

    @Override
    public void removeBusinessEmployeeById(Jwt jwt, String businessId, String employeeId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business.getOwnerId().equals(employeeId))
            throw new BadBusinessRequestException();

        if (!businessRepository.existsByBusinessId_BusinessId(businessId))
            throw new BusinessNotFoundException(businessId);

        List<Employee> employees = employeeRepository.findAllByBusinessId_BusinessId(businessId);

        jwtUtils.validateUserIsEmployeeOfBusiness(jwt, businessId);

        Employee employee = employees.stream().filter(e -> e.getEmployeeId().getEmployeeId().equals(employeeId)).findFirst().orElse(null);

        if (employee == null)
            throw new BusinessEmployeeNotFoundException(employeeId);

        employeeRepository.delete(employee);
    }

    @Override
    public BusinessResponseModel getBusinessByUserId(Jwt jwt, String userId) {
        String authenticatedUserId = jwtUtils.extractUserId(jwt);
        if (!userId.equals(authenticatedUserId))
            throw new AccessDeniedException("Access Denied");

        Employee employee = employeeRepository.findByUserId(userId);
        if (employee == null)
            throw new BusinessEmployeeNotFoundException(userId);

        String businessId = employee.getBusinessId().getBusinessId();
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        return businessMapper.toResponse(business);
    }

    private VerificationContext updateBusinessVerification(String businessId, String verificationId){
        Verification verification = verificationRepository.findVerificationByVerificationId_VerificationId(verificationId);
        if (verification == null)
            throw new VerificationNotFoundException();

        if (verification.getStatus() != VerificationStatus.PENDING)
            throw new BadVerificationRequestException();

        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        if (business.isVerified())
            throw new BusinessAlreadyVerifiedException();

        return new VerificationContext(verification, business);
    }

    private record VerificationContext(Verification verification, Business business) {}
}
