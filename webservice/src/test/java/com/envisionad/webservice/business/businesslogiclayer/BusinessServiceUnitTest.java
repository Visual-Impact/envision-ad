package com.envisionad.webservice.business.businesslogiclayer;

import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.business.exceptions.*;
import com.envisionad.webservice.business.mappinglayer.BusinessMapper;
import com.envisionad.webservice.business.mappinglayer.EmployeeMapper;
import com.envisionad.webservice.business.mappinglayer.InvitationMapper;
import com.envisionad.webservice.business.mappinglayer.VerificationMapper;
import com.envisionad.webservice.business.presentationlayer.models.BusinessRequestModel;
import com.envisionad.webservice.business.presentationlayer.models.InvitationRequestModel;
import com.envisionad.webservice.utils.EmailService;
import com.envisionad.webservice.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceUnitTest {
    @InjectMocks
    private BusinessServiceImpl businessService;

    @Mock
    private EmailService emailService;

    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private InvitationMapper invitationMapper;

    @Mock
    private VerificationMapper verificationMapper;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private VerificationRepository verificationRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private com.envisionad.webservice.config.Auth0Service auth0Service;

    private static final String BUSINESS_ID = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
    private static final String NOT_FOUND_BUSINESS_ID = "5d4bd18a-e062-4dc8-9715-f02dea0b3f99";
    private static final String VERIFICATION_ID = "b3888155-81dd-44f2-9448-230b40558e71";
    private static final String INVITATION_ID = "1de946c2-926d-417f-b782-362070e3c73c";
    private static final String INVITATION_TOKEN = "c59e5919-3d3c-4bff-a831-c5c61d3c492a";
    private static final String TEST_EMAIL = "test@email.com";

    private Jwt adminToken;
    private Jwt mediaToken;
    private Jwt advertiserToken;
    private Jwt newUserToken;

    private Address testAddress;
    private Roles mediaOwnerRoles;

    @BeforeEach
    void setUp() {
        adminToken = createJwtToken("admin-token", "auth0|696a89377cfdb558ea4a4a61",
                List.of("readAll:verification", "update:verification"));

        mediaToken = createJwtToken("media-token", "auth0|696a89137cfdb558ea4a4a4a",
                List.of("create:media", "update:media", "update:business", "read:employee",
                        "create:employee", "delete:employee", "read:verification", "create:verification"));

        advertiserToken = createJwtToken("advertiser-token", "auth0|696a88eb347945897ef17093",
                List.of("read:campaign", "create:campaign", "update:campaign", "update:business",
                        "read:employee", "create:employee", "delete:employee", "read:verification", "create:verification"));

        newUserToken = createJwtToken("newUser-token", "auth0|696b10a00bba0a28c21d3829", List.of());

        testAddress = createTestAddress();
        mediaOwnerRoles = createMediaOwnerRoles();
    }

    private Jwt createJwtToken(String tokenValue, String subject, List<String> permissions) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("sub", subject)
                .claim("scope", "read write")
                .claim("permissions", permissions)
                .build();
    }

    private Address createTestAddress() {
        Address address = new Address();
        address.setCity("St-Lambert");
        address.setStreet("900 Riverside");
        address.setCountry("Canada");
        address.setState("QC");
        address.setZipCode("J4P 3P2");
        return address;
    }

    private Roles createMediaOwnerRoles() {
        Roles roles = new Roles();
        roles.setMediaOwner(true);
        return roles;
    }

    private BusinessRequestModel createBusinessRequestModel() {
        BusinessRequestModel model = new BusinessRequestModel();
        model.setName("Champlain College");
        model.setOrganizationSize(OrganizationSize.SMALL);
        model.setAddress(testAddress);
        model.setRoles(mediaOwnerRoles);
        return model;
    }

    private Business createBusiness(boolean verified) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
        business.setName("Champlain College");
        business.setOrganizationSize(OrganizationSize.SMALL);
        business.setVerified(verified);
        business.setAddress(testAddress);
        business.setRoles(mediaOwnerRoles);
        return business;
    }

    private Invitation createInvitation(String businessId, String invitationToken, LocalDateTime timeCreated, LocalDateTime timeExpires) {
        Invitation invitation = new Invitation();
        invitation.setBusinessId(new BusinessIdentifier(businessId));
        invitation.setInvitationId(new InvitationIdentifier(INVITATION_ID));
        invitation.setToken(invitationToken);
        invitation.setEmail(TEST_EMAIL);
        invitation.setTimeCreated(timeCreated);
        invitation.setTimeExpires(timeExpires);
        return invitation;
    }

    private Verification createVerification(String businessId, String verificationId, VerificationStatus status) {
        Verification verification = new Verification();
        verification.setVerificationId(new VerificationIdentifier(verificationId));
        verification.setBusinessId(new BusinessIdentifier(businessId));
        verification.setStatus(status);
        verification.setDateCreated(LocalDateTime.now());
        verification.setDateModified(LocalDateTime.now());
        return verification;
    }

    @Test
    public void whenGetBusinessById_withNotFoundId_ThenReturnBusinessNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.getBusinessById(NOT_FOUND_BUSINESS_ID));
    }

    @Test
    public void whenUpdateBusinessById_withNotFoundId_ThenReturnBusinessNotFoundException() {
        BusinessRequestModel businessRequestModel = createBusinessRequestModel();

        when(businessRepository.findByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.updateBusinessById(mediaToken, NOT_FOUND_BUSINESS_ID, businessRequestModel));
    }

    @Test
    public void whenAddBusinessEmployee_withNotFoundId_ThenReturnBusinessNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.addBusinessEmployee(mediaToken, NOT_FOUND_BUSINESS_ID, INVITATION_TOKEN));
    }

    @Test
    public void whenAddBusinessEmployee_withNotFoundInvitationId_ThenReturnInvitationNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(null);

        assertThrows(InvitationNotFoundException.class,
                () -> businessService.addBusinessEmployee(mediaToken, BUSINESS_ID, INVITATION_TOKEN));
    }

    @Test
    public void whenAddBusinessEmployee_withExpiredInvitation_ThenReturnInvitationNotFoundException() {
        LocalDateTime pastTime = LocalDateTime.of(2025, 1, 17, 16, 0);
        LocalDateTime expiredTime = LocalDateTime.of(2025, 1, 17, 17, 0);
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, pastTime, expiredTime);

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);

        assertThrows(InvitationNotFoundException.class,
                () -> businessService.addBusinessEmployee(mediaToken, BUSINESS_ID, INVITATION_TOKEN));

        verify(invitationRepository).delete(invitation);
    }

    @Test
    public void whenAddBusinessEmployee_withEmployeeAlreadyInBusiness_ThenReturnAccessDenied() {
        LocalDateTime now = LocalDateTime.now();
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, now, now.plusHours(1));

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);
        when(jwtUtils.extractUserId(mediaToken)).thenReturn("auth0|696a89137cfdb558ea4a4a4a");
        when(employeeRepository.existsByUserIdAndBusinessId_BusinessId("auth0|696a89137cfdb558ea4a4a4a", BUSINESS_ID))
                .thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> businessService.addBusinessEmployee(mediaToken, BUSINESS_ID, INVITATION_TOKEN));
    }

    @Test
    public void whenAddBusinessEmployee_withValidJwt_ThenReturnsAccepted() {
        LocalDateTime now = LocalDateTime.now();
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, now, now.plusHours(1));
        Employee savedEmployee = new Employee();
        savedEmployee.setEmployeeId(new EmployeeIdentifier(INVITATION_ID));
        savedEmployee.setUserId("auth0|696a89137cfdb558ea4a4a4a");

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);
        when(jwtUtils.extractUserId(mediaToken)).thenReturn("auth0|696a89137cfdb558ea4a4a4a");
        when(employeeRepository.existsByUserIdAndBusinessId_BusinessId("auth0|696a89137cfdb558ea4a4a4a", BUSINESS_ID))
                .thenReturn(false);
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(savedEmployee);

        com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptResponseModel result =
                businessService.addBusinessEmployee(mediaToken, BUSINESS_ID, INVITATION_TOKEN);

        assertEquals(com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptStatus.ACCEPTED,
                result.getStatus());
        verify(invitationRepository).delete(invitation);
        verify(auth0Service, org.mockito.Mockito.never()).findUserIdByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void whenAddBusinessEmployee_withNoJwtAndExistingAuth0Account_ThenReturnsLoginRequiredWithoutDeletingInvitation() {
        LocalDateTime now = LocalDateTime.now();
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, now, now.plusHours(1));

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);
        when(auth0Service.findUserIdByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.of("auth0|existing"));

        com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptResponseModel result =
                businessService.addBusinessEmployee(null, BUSINESS_ID, INVITATION_TOKEN);

        assertEquals(com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptStatus.LOGIN_REQUIRED,
                result.getStatus());
        assertNull(result.getEmployee());
        // Left intact — the follow-up authenticated call (after the client logs in) must
        // still be able to find this same invitation by token.
        verify(invitationRepository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
        verify(auth0Service, org.mockito.Mockito.never()).createUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void whenAddBusinessEmployee_withNoJwtAndNoExistingAccount_ThenProvisionsUserAndReturnsProvisioned() {
        LocalDateTime now = LocalDateTime.now();
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, now, now.plusHours(1));
        invitation.setName("Jane Doe");
        Employee savedEmployee = new Employee();
        savedEmployee.setEmployeeId(new EmployeeIdentifier(INVITATION_ID));
        savedEmployee.setUserId("auth0|brandnew");

        com.envisionad.webservice.business.presentationlayer.models.EmployeeResponseModel employeeResponse =
                new com.envisionad.webservice.business.presentationlayer.models.EmployeeResponseModel();
        employeeResponse.setEmployeeId(INVITATION_ID);
        employeeResponse.setUserId("auth0|brandnew");

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);
        when(auth0Service.findUserIdByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.empty());
        when(auth0Service.createUser(TEST_EMAIL, "Jane Doe")).thenReturn("auth0|brandnew");
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(savedEmployee);
        when(employeeMapper.toResponse(savedEmployee)).thenReturn(employeeResponse);
        when(auth0Service.createPasswordChangeTicket(org.mockito.ArgumentMatchers.eq("auth0|brandnew"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://auth0.example.com/ticket/xyz");

        com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptResponseModel result =
                businessService.addBusinessEmployee(null, BUSINESS_ID, INVITATION_TOKEN);

        assertEquals(com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptStatus.PROVISIONED,
                result.getStatus());
        assertEquals("auth0|brandnew", result.getEmployee().getUserId());
        verify(invitationRepository).delete(invitation);
        verify(emailService).sendSimpleEmail(org.mockito.ArgumentMatchers.eq(TEST_EMAIL), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        // createBusiness(true) is media-owner-only — the new employee should get exactly
        // MEDIA_OWNER, matching the business's own roles (colleagues share the business's
        // role flags; there's no per-employee role selection in this data model).
        verify(auth0Service).assignRoles("auth0|brandnew", java.util.List.of(com.envisionad.webservice.config.Auth0Roles.MEDIA_OWNER));
    }

    @Test
    public void whenAddBusinessEmployee_withNoJwtAndTicketCreationFails_ThenStillReturnsProvisioned() {
        LocalDateTime now = LocalDateTime.now();
        Invitation invitation = createInvitation(BUSINESS_ID, INVITATION_TOKEN, now, now.plusHours(1));
        Employee savedEmployee = new Employee();
        savedEmployee.setEmployeeId(new EmployeeIdentifier(INVITATION_ID));
        savedEmployee.setUserId("auth0|brandnew");

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(createBusiness(true));
        when(invitationRepository.findByToken(INVITATION_TOKEN)).thenReturn(invitation);
        when(auth0Service.findUserIdByEmail(TEST_EMAIL)).thenReturn(java.util.Optional.empty());
        // No name set on this invitation — falls back to the invitation's email.
        when(auth0Service.createUser(TEST_EMAIL, TEST_EMAIL)).thenReturn("auth0|brandnew");
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class))).thenReturn(savedEmployee);
        when(auth0Service.createPasswordChangeTicket(org.mockito.ArgumentMatchers.eq("auth0|brandnew"), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new com.envisionad.webservice.config.exceptions.Auth0ServiceUnavailableException("boom", null));

        com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptResponseModel result =
                businessService.addBusinessEmployee(null, BUSINESS_ID, INVITATION_TOKEN);

        // The employee row is already saved — a ticket failure is a "resend" problem,
        // not a reason to fail the whole accept.
        assertEquals(com.envisionad.webservice.business.presentationlayer.models.InvitationAcceptStatus.PROVISIONED,
                result.getStatus());
        verify(emailService, org.mockito.Mockito.never()).sendSimpleEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void whenApproveBusinessVerification_withNotFoundVerificationId_ThenReturnVerificationNotFoundException() {
        when(verificationRepository.findVerificationByVerificationId_VerificationId(VERIFICATION_ID)).thenReturn(null);

        assertThrows(VerificationNotFoundException.class,
                () -> businessService.approveBusinessVerification(BUSINESS_ID, VERIFICATION_ID));
    }

    @Test
    public void whenApproveBusinessVerification_withAlreadyApprovedVerification_ThenReturnBadVerificationRequestException() {
        Verification verification = createVerification(BUSINESS_ID, VERIFICATION_ID, VerificationStatus.APPROVED);

        when(verificationRepository.findVerificationByVerificationId_VerificationId(VERIFICATION_ID))
                .thenReturn(verification);

        assertThrows(BadVerificationRequestException.class,
                () -> businessService.approveBusinessVerification(BUSINESS_ID, VERIFICATION_ID));
    }

    @Test
    public void whenApproveBusinessVerification_withBusinessIdNotFound_ThenReturnBusinessNotFoundException() {
        String differentBusinessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b23";
        Verification verification = createVerification(differentBusinessId, VERIFICATION_ID, VerificationStatus.PENDING);

        when(verificationRepository.findVerificationByVerificationId_VerificationId(VERIFICATION_ID))
                .thenReturn(verification);
        when(businessRepository.findByBusinessId_BusinessId(differentBusinessId)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.approveBusinessVerification(differentBusinessId, VERIFICATION_ID));
    }

    @Test
    public void whenApproveBusinessVerification_withAlreadyVerifiedBusiness_ThenReturnBusinessAlreadyVerifiedException() {
        Verification verification = createVerification(BUSINESS_ID, VERIFICATION_ID, VerificationStatus.PENDING);
        Business business = createBusiness(true);

        when(verificationRepository.findVerificationByVerificationId_VerificationId(VERIFICATION_ID))
                .thenReturn(verification);
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);

        assertThrows(BusinessAlreadyVerifiedException.class,
                () -> businessService.approveBusinessVerification(BUSINESS_ID, VERIFICATION_ID));
    }

    @Test
    public void whenCreateBusiness_withDuplicateBusinessName_thenReturnDuplicateBusinessNameException() {
        BusinessRequestModel businessRequestModel = createBusinessRequestModel();

        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot("Champlain College", null)).thenReturn(true);

        assertThrows(DuplicateBusinessNameException.class,
                () -> businessService.createBusiness(newUserToken, businessRequestModel));
    }

    @Test
    public void whenCreateBusiness_withUserAlreadyInBusiness_thenReturnAccessDeniedException() {
        BusinessRequestModel businessRequestModel = createBusinessRequestModel();

        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot("Champlain College", null)).thenReturn(false);
        when(jwtUtils.extractUserId(mediaToken)).thenReturn("auth0|696a89137cfdb558ea4a4a4a");
        when(employeeRepository.existsByUserId("auth0|696a89137cfdb558ea4a4a4a")).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> businessService.createBusiness(mediaToken, businessRequestModel));
    }

    @Test
    public void whenUpdateBusiness_withDuplicateBusinessName_thenReturnDuplicateBusinessNameException() {
        BusinessRequestModel businessRequestModel = createBusinessRequestModel();
        Business business = createBusiness(true);

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot("Champlain College", BUSINESS_ID)).thenReturn(true);

        assertThrows(DuplicateBusinessNameException.class,
                () -> businessService.updateBusinessById(newUserToken, BUSINESS_ID, businessRequestModel));
    }

    @Test
    public void whenDenyBusinessVerification_withNullReason_thenReturnBadVerificationRequestException() {
        assertThrows(BadVerificationRequestException.class,
                () -> businessService.denyBusinessVerification(BUSINESS_ID, VERIFICATION_ID, null));
    }

    @Test
    public void whenDenyBusinessVerification_withEmptyReason_thenReturnBadVerificationRequestException() {
        assertThrows(BadVerificationRequestException.class,
                () -> businessService.denyBusinessVerification(BUSINESS_ID, VERIFICATION_ID, ""));
    }

    @Test
    public void whenDenyBusinessVerification_withToLongReason_thenReturnBadVerificationRequestException() {
        String longReason = "x".repeat(513);

        assertThrows(BadVerificationRequestException.class,
                () -> businessService.denyBusinessVerification(BUSINESS_ID, VERIFICATION_ID, longReason));
    }

    @Test
    public void whenRequestBusinessVerification_withNotFoundBusinessId_thenReturnBusinessNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.requestVerification(mediaToken, NOT_FOUND_BUSINESS_ID));
    }

    @Test
    public void whenRequestBusinessVerification_withAlreadyVerifiedBusiness_thenReturnBusinessAlreadyVerifiedException() {
        Business business = createBusiness(true);

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);

        assertThrows(BusinessAlreadyVerifiedException.class,
                () -> businessService.requestVerification(mediaToken, BUSINESS_ID));
    }

    @Test
    public void whenRequestBusinessVerification_withAlreadyPendingVerification_thenReturnBusinessAlreadyVerifiedException() {
        Business business = createBusiness(false);
        Verification verification = createVerification(BUSINESS_ID, VERIFICATION_ID, VerificationStatus.PENDING);

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        when(verificationRepository.findAllByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(List.of(verification));

        assertThrows(BadVerificationRequestException.class,
                () -> businessService.requestVerification(mediaToken, BUSINESS_ID));
    }

    @Test
    public void whenCreateInvitation_withNotFoundBusinessId_thenReturnBusinessNotFoundException() {
        String email = "test@email.com";
        InvitationRequestModel invitationRequestModel = new InvitationRequestModel();
        invitationRequestModel.setEmail(email);

        when(businessRepository.findByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.createInvitation(mediaToken, NOT_FOUND_BUSINESS_ID, invitationRequestModel));
    }

    @Test
    public void whenCreateInvitation_withAlreadyExistingEmail_thenReturnBadInvitationRequestException() {
        String email = "test@email.com";
        InvitationRequestModel invitationRequestModel = new InvitationRequestModel();
        invitationRequestModel.setEmail(email);

        Invitation invitation = new Invitation();
        invitation.setEmail(email);

        Business business = createBusiness(true);

        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        when(invitationRepository.existsByBusinessId_BusinessIdAndEmail(BUSINESS_ID, email)).thenReturn(true);
        when(invitationMapper.toEntity(invitationRequestModel)).thenReturn(invitation);

        assertThrows(BadInvitationRequestException.class,
                () -> businessService.createInvitation(mediaToken, BUSINESS_ID, invitationRequestModel));
    }

    @Test
    public void whenCancelInvitation_withNotFoundBusinessId_thenReturnBusinessNotFoundException(){
        when(businessRepository.existsByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(false);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.cancelInvitation(mediaToken, NOT_FOUND_BUSINESS_ID, INVITATION_ID));
    }

    @Test
    public void whenCancelInvitation_withNotInvitationId_thenReturnInvitationNotFoundException(){
        when(businessRepository.existsByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(true);
        when(invitationRepository.findByInvitationId_InvitationId(INVITATION_ID)).thenReturn(null);

        assertThrows(InvitationNotFoundException.class, () -> businessService.cancelInvitation(mediaToken, BUSINESS_ID, INVITATION_ID));
    }

    @Test
    public void whenGetAllBusinessEmployees_withNotFoundBusinessId_thenReturnBusinessNotFoundException(){
        when(businessRepository.existsByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(false);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.getAllEmployeesByBusinessId(mediaToken, NOT_FOUND_BUSINESS_ID));
    }

    @Test
    public void whenGetAllBusinessInvitation_withNotFoundBusinessId_thenReturnBusinessNotFoundException(){
        when(businessRepository.existsByBusinessId_BusinessId(NOT_FOUND_BUSINESS_ID)).thenReturn(false);

        assertThrows(BusinessNotFoundException.class,
                () -> businessService.getAllInvitationsByBusinessId(mediaToken, NOT_FOUND_BUSINESS_ID));
    }
}