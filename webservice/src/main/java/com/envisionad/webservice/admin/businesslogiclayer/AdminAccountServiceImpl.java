package com.envisionad.webservice.admin.businesslogiclayer;

import com.envisionad.webservice.admin.exceptions.DuplicateAccountException;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountListItemModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountRequestModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.business.exceptions.DuplicateBusinessNameException;
import com.envisionad.webservice.business.mappinglayer.BusinessMapper;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import com.envisionad.webservice.business.utils.Validator;
import com.envisionad.webservice.config.Auth0Roles;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.utils.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the admin "create client account" flow (brief FR 4.2): Auth0 user →
 * Business + Employee → role assignment + password-change ticket + welcome email.
 * The whole method runs inside one @Transactional boundary — matching
 * CouponServiceImpl's existing external-API-plus-DB pattern in this codebase — but
 * every step-(d) failure (role assignment, ticket, email) is caught and turned into a
 * warning rather than rethrown, so it can never trigger a rollback of the already-good
 * Business/Employee rows. That's what makes those steps "best-effort after the DB
 * commit" (FR 4.2.d) even though they execute before the physical commit completes.
 */
@Slf4j
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    @Value("${app.base.url}")
    private String appBaseUrl;

    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessMapper businessMapper;
    private final Auth0Service auth0Service;
    private final EmailService emailService;

    public AdminAccountServiceImpl(BusinessRepository businessRepository, EmployeeRepository employeeRepository,
            BusinessMapper businessMapper, Auth0Service auth0Service, EmailService emailService) {
        this.businessRepository = businessRepository;
        this.employeeRepository = employeeRepository;
        this.businessMapper = businessMapper;
        this.auth0Service = auth0Service;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public AdminAccountResponseModel createAccount(AdminAccountRequestModel request) {
        Validator.validateBusiness(request.getBusiness());

        // FR 4.2.a — Auth0 is the only source of truth for email; the local schema has
        // no email column to check against (P5-PROGRESS.md ground truth item 2).
        if (auth0Service.findUserIdByEmail(request.getEmail()).isPresent())
            throw new DuplicateAccountException(request.getEmail());

        if (businessRepository.existsByNameAndBusinessId_BusinessIdNot(request.getBusiness().getName(), null))
            throw new DuplicateBusinessNameException();

        String userId = auth0Service.createUser(request.getEmail(), request.getName());

        Business business;
        try {
            business = businessMapper.toEntity(request.getBusiness());
            business.setBusinessId(new BusinessIdentifier());
            business.setOwnerId(userId);
            business.setVerified(true);
            business.setActive(true);

            Employee employee = new Employee();
            employee.setEmployeeId(new EmployeeIdentifier());
            employee.setBusinessId(business.getBusinessId());
            employee.setUserId(userId);

            businessRepository.save(business);
            employeeRepository.save(employee);
        } catch (RuntimeException dbFailure) {
            // FR 4.2.c — compensate the just-created Auth0 user; the DB half rolls back
            // on its own via @Transactional.
            log.error("Admin account creation failed after Auth0 user {} was created; compensating with delete",
                    userId, dbFailure);
            auth0Service.deleteUser(userId);
            throw dbFailure;
        }

        List<String> warnings = new ArrayList<>();
        assignRolesBestEffort(userId, business.getRoles(), warnings);
        String ticketUrl = createPasswordTicketBestEffort(userId, warnings);
        sendWelcomeEmailBestEffort(request.getEmail(), request.getName(), business.getName(), ticketUrl, warnings);

        AdminAccountResponseModel response = new AdminAccountResponseModel();
        response.setBusiness(businessMapper.toResponse(business));
        response.setOwnerUserId(userId);
        response.setWarnings(warnings);
        return response;
    }

    @Override
    public void resendCredentials(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        String ticketUrl = auth0Service.createPasswordChangeTicket(business.getOwnerId(), appBaseUrl + "/auth/login");
        String email = auth0Service.getUserEmailByUserId(business.getOwnerId());

        String subject = "Your Envision Ad account is ready";
        String body = "Hi there,\n\n"
                + "Here's a fresh link to set your password for " + business.getName() + " on Envision Ad:\n"
                + ticketUrl + "\n\n"
                + "Once set, log in here:\n" + appBaseUrl + "/auth/login\n\n"
                + "— The Envision Ad Team";
        emailService.sendSimpleEmail(email, subject, body);
    }

    @Override
    public BusinessResponseModel setActive(String businessId, boolean active) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        business.setActive(active);
        businessRepository.save(business);
        // Mirrors to Auth0 after the local write (FR 4.4/5.5). No compensation on Auth0
        // failure here — the admin table already reflects the new state and the action
        // is idempotent, so retrying this same call is the recovery path.
        auth0Service.setUserBlocked(business.getOwnerId(), !active);

        return businessMapper.toResponse(business);
    }

    @Override
    public List<AdminAccountListItemModel> getAllAccounts() {
        return businessRepository.findAll().stream().map(business -> {
            AdminAccountListItemModel item = new AdminAccountListItemModel();
            item.setBusinessId(business.getBusinessId().getBusinessId());
            item.setName(business.getName());
            item.setOwnerEmail(safeGetOwnerEmail(business.getOwnerId()));
            item.setRoles(business.getRoles());
            item.setBusinessTypeVenueId(business.getBusinessTypeVenueId());
            item.setActive(business.isActive());
            item.setDateCreated(business.getDateCreated());
            return item;
        }).toList();
    }

    // One broken Auth0 lookup (e.g. a stale/manually-deleted user) shouldn't 500 the
    // whole admin table — degrade that one row instead of failing the list.
    private String safeGetOwnerEmail(String ownerId) {
        try {
            return auth0Service.getUserEmailByUserId(ownerId);
        } catch (RuntimeException e) {
            log.warn("Could not resolve owner email for business owner {}", ownerId, e);
            return null;
        }
    }

    private void assignRolesBestEffort(String userId, Roles roles, List<String> warnings) {
        try {
            List<String> roleIds = new ArrayList<>();
            roleIds.add(Auth0Roles.BUSINESS_OWNER);
            if (roles.isAdvertiser())
                roleIds.add(Auth0Roles.ADVERTISER);
            if (roles.isMediaOwner())
                roleIds.add(Auth0Roles.MEDIA_OWNER);
            auth0Service.assignRoles(userId, roleIds);
        } catch (RuntimeException e) {
            log.warn("Role assignment failed for newly created admin account, user {}", userId, e);
            warnings.add("Role assignment may not have completed. Use Resend Credentials or contact support if the client reports missing permissions.");
        }
    }

    private String createPasswordTicketBestEffort(String userId, List<String> warnings) {
        try {
            return auth0Service.createPasswordChangeTicket(userId, appBaseUrl + "/auth/login");
        } catch (RuntimeException e) {
            log.warn("Password-change ticket creation failed for user {}", userId, e);
            warnings.add("Password-change link could not be generated. Use Resend Credentials to retry.");
            return null;
        }
    }

    private void sendWelcomeEmailBestEffort(String email, String name, String businessName, String ticketUrl,
            List<String> warnings) {
        if (ticketUrl == null) {
            warnings.add("Welcome email not sent — no password-change link was available.");
            return;
        }
        try {
            String subject = "Your Envision Ad account is ready";
            String body = "Hi " + (name != null && !name.isBlank() ? name : "there") + ",\n\n"
                    + "An Envision Ad account has been created for " + businessName + ".\n\n"
                    + "Set your password to get started:\n" + ticketUrl + "\n\n"
                    + "Once set, log in here:\n" + appBaseUrl + "/auth/login\n\n"
                    + "— The Envision Ad Team";
            emailService.sendSimpleEmail(email, subject, body);
        } catch (RuntimeException e) {
            log.warn("Welcome email failed to send to {}", email, e);
            warnings.add("Welcome email may not have sent. Use Resend Credentials to retry.");
        }
    }
}
