package com.envisionad.webservice.admin.businesslogiclayer;

import com.envisionad.webservice.admin.exceptions.DuplicateAccountException;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountListItemModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountRequestModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.Address;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.business.dataaccesslayer.OrganizationSize;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.business.exceptions.DuplicateBusinessNameException;
import com.envisionad.webservice.business.mappinglayer.BusinessMapper;
import com.envisionad.webservice.business.presentationlayer.models.BusinessRequestModel;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import com.envisionad.webservice.config.Auth0Roles;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.config.exceptions.Auth0ServiceUnavailableException;
import com.envisionad.webservice.utils.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-level throughout — createAccount reaches Auth0 for every call in FR 4.2's
 * orchestration, and BaseIntegrationTest cannot stub that (same constraint
 * CouponServiceImplUnitTest documents for Stripe). Mirrors that file's structure.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountServiceImplUnitTest {

    @InjectMocks
    private AdminAccountServiceImpl adminAccountService;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private Auth0Service auth0Service;

    @Mock
    private EmailService emailService;

    private static final String EMAIL = "new-client@example.com";
    private static final String NAME = "Jane Doe";
    private static final String USER_ID = "auth0|newuser123";
    private static final String BUSINESS_ID = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
    private static final String TICKET_URL = "https://auth0.example.com/ticket/abc";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminAccountService, "appBaseUrl", "https://app.example.com");
    }

    private AdminAccountRequestModel validRequest(boolean advertiser, boolean mediaOwner) {
        AdminAccountRequestModel request = new AdminAccountRequestModel();
        request.setEmail(EMAIL);
        request.setName(NAME);

        BusinessRequestModel business = new BusinessRequestModel();
        business.setName("Acme Signage");
        business.setOrganizationSize(OrganizationSize.SMALL);
        Address address = new Address();
        address.setStreet("123 Main St");
        address.setCity("Montreal");
        address.setState("QC");
        address.setZipCode("H2X 1Y1");
        address.setCountry("Canada");
        business.setAddress(address);
        Roles roles = new Roles();
        roles.setAdvertiser(advertiser);
        roles.setMediaOwner(mediaOwner);
        business.setRoles(roles);
        request.setBusiness(business);
        return request;
    }

    private Business entityFor(AdminAccountRequestModel request) {
        Business business = new Business();
        business.setName(request.getBusiness().getName());
        business.setOrganizationSize(request.getBusiness().getOrganizationSize());
        business.setAddress(request.getBusiness().getAddress());
        business.setRoles(request.getBusiness().getRoles());
        return business;
    }

    // =========================================================================
    // createAccount — happy path
    // =========================================================================

    @Test
    void whenCreateAccount_withValidRequest_thenProvisionsEverythingAndReturnsNoWarnings() {
        AdminAccountRequestModel request = validRequest(true, false);
        Business mappedEntity = entityFor(request);

        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot(anyString(), any())).thenReturn(false);
        when(auth0Service.createUser(EMAIL, NAME)).thenReturn(USER_ID);
        when(businessMapper.toEntity(request.getBusiness())).thenReturn(mappedEntity);
        when(auth0Service.createPasswordChangeTicket(eq(USER_ID), anyString())).thenReturn(TICKET_URL);
        when(businessMapper.toResponse(mappedEntity)).thenReturn(new BusinessResponseModel());

        AdminAccountResponseModel result = adminAccountService.createAccount(request);

        assertEquals(USER_ID, result.getOwnerUserId());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(USER_ID, mappedEntity.getOwnerId());
        assertTrue(mappedEntity.isVerified());
        assertTrue(mappedEntity.isActive());

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertEquals(USER_ID, employeeCaptor.getValue().getUserId());

        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(auth0Service).assignRoles(eq(USER_ID), rolesCaptor.capture());
        assertTrue(rolesCaptor.getValue().contains(Auth0Roles.BUSINESS_OWNER));
        assertTrue(rolesCaptor.getValue().contains(Auth0Roles.ADVERTISER));
        assertFalse(rolesCaptor.getValue().contains(Auth0Roles.MEDIA_OWNER));

        verify(emailService).sendSimpleEmail(eq(EMAIL), anyString(), anyString());
        verify(auth0Service, never()).deleteUser(anyString());
    }

    // =========================================================================
    // createAccount — duplicate guards (FR 5.1 / 5.4)
    // =========================================================================

    @Test
    void whenCreateAccount_andEmailAlreadyExists_thenThrowsDuplicateAccountExceptionBeforeAnyAuth0Write() {
        AdminAccountRequestModel request = validRequest(true, false);
        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.of("auth0|existing"));

        assertThrows(DuplicateAccountException.class, () -> adminAccountService.createAccount(request));

        verify(auth0Service, never()).createUser(anyString(), anyString());
        verify(businessRepository, never()).save(any());
    }

    @Test
    void whenCreateAccount_andBusinessNameAlreadyExists_thenThrowsDuplicateBusinessNameException() {
        AdminAccountRequestModel request = validRequest(true, false);
        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot(anyString(), any())).thenReturn(true);

        assertThrows(DuplicateBusinessNameException.class, () -> adminAccountService.createAccount(request));

        verify(auth0Service, never()).createUser(anyString(), anyString());
    }

    // =========================================================================
    // createAccount — FR 4.2.c compensating delete
    // =========================================================================

    @Test
    void whenCreateAccount_andDbWriteFails_thenCompensatesByDeletingTheAuth0UserAndRethrows() {
        AdminAccountRequestModel request = validRequest(true, false);
        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot(anyString(), any())).thenReturn(false);
        when(auth0Service.createUser(EMAIL, NAME)).thenReturn(USER_ID);
        when(businessMapper.toEntity(request.getBusiness())).thenReturn(entityFor(request));
        when(businessRepository.save(any())).thenThrow(new RuntimeException("db unavailable"));

        assertThrows(RuntimeException.class, () -> adminAccountService.createAccount(request));

        verify(auth0Service).deleteUser(USER_ID);
        verify(employeeRepository, never()).save(any());
    }

    // =========================================================================
    // createAccount — FR 4.2.d partial-success warnings
    // =========================================================================

    @Test
    void whenCreateAccount_andRoleAssignmentFails_thenReturnsWarningInsteadOfThrowing() {
        AdminAccountRequestModel request = validRequest(false, true);
        Business mappedEntity = entityFor(request);

        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot(anyString(), any())).thenReturn(false);
        when(auth0Service.createUser(EMAIL, NAME)).thenReturn(USER_ID);
        when(businessMapper.toEntity(request.getBusiness())).thenReturn(mappedEntity);
        doThrow(new Auth0ServiceUnavailableException("boom", null))
                .when(auth0Service).assignRoles(eq(USER_ID), any());
        when(auth0Service.createPasswordChangeTicket(eq(USER_ID), anyString())).thenReturn(TICKET_URL);
        when(businessMapper.toResponse(mappedEntity)).thenReturn(new BusinessResponseModel());

        AdminAccountResponseModel result = adminAccountService.createAccount(request);

        assertFalse(result.getWarnings().isEmpty());
        // The business/employee rows and the account itself still exist — no rollback.
        verify(businessRepository).save(mappedEntity);
        verify(auth0Service, never()).deleteUser(anyString());
    }

    @Test
    void whenCreateAccount_andTicketCreationFails_thenSkipsEmailAndWarnsForBoth() {
        AdminAccountRequestModel request = validRequest(true, true);
        Business mappedEntity = entityFor(request);

        when(auth0Service.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(businessRepository.existsByNameAndBusinessId_BusinessIdNot(anyString(), any())).thenReturn(false);
        when(auth0Service.createUser(EMAIL, NAME)).thenReturn(USER_ID);
        when(businessMapper.toEntity(request.getBusiness())).thenReturn(mappedEntity);
        when(auth0Service.createPasswordChangeTicket(eq(USER_ID), anyString()))
                .thenThrow(new Auth0ServiceUnavailableException("boom", null));
        when(businessMapper.toResponse(mappedEntity)).thenReturn(new BusinessResponseModel());

        AdminAccountResponseModel result = adminAccountService.createAccount(request);

        assertEquals(2, result.getWarnings().size());
        verify(emailService, never()).sendSimpleEmail(anyString(), anyString(), anyString());
    }

    // =========================================================================
    // resendCredentials (FR 4.3)
    // =========================================================================

    @Test
    void whenResendCredentials_withExistingBusiness_thenReissuesTicketAndResendsEmail() {
        Business business = new Business();
        business.setName("Acme Signage");
        business.setOwnerId(USER_ID);
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        when(auth0Service.createPasswordChangeTicket(eq(USER_ID), anyString())).thenReturn(TICKET_URL);
        when(auth0Service.getUserEmailByUserId(USER_ID)).thenReturn(EMAIL);

        adminAccountService.resendCredentials(BUSINESS_ID);

        verify(emailService).sendSimpleEmail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void whenResendCredentials_andBusinessNotFound_thenThrowsBusinessNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class, () -> adminAccountService.resendCredentials(BUSINESS_ID));

        verify(auth0Service, never()).createPasswordChangeTicket(anyString(), anyString());
    }

    // =========================================================================
    // setActive (FR 4.4 / 5.5)
    // =========================================================================

    @Test
    void whenSetActive_toFalse_thenBlocksTheAuth0UserAndPersistsLocally() {
        Business business = new Business();
        business.setOwnerId(USER_ID);
        business.setActive(true);
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(business);
        when(businessMapper.toResponse(business)).thenReturn(new BusinessResponseModel());

        adminAccountService.setActive(BUSINESS_ID, false);

        assertFalse(business.isActive());
        verify(businessRepository).save(business);
        verify(auth0Service).setUserBlocked(USER_ID, true);
    }

    @Test
    void whenSetActive_andBusinessNotFound_thenThrowsBusinessNotFoundException() {
        when(businessRepository.findByBusinessId_BusinessId(BUSINESS_ID)).thenReturn(null);

        assertThrows(BusinessNotFoundException.class, () -> adminAccountService.setActive(BUSINESS_ID, true));
    }

    // =========================================================================
    // getAllAccounts (FR 4.5)
    // =========================================================================

    @Test
    void whenGetAllAccounts_thenResolvesOwnerEmailsViaOneBatchCall() {
        Business business = new Business();
        business.setBusinessId(new com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier(BUSINESS_ID));
        business.setName("Acme Signage");
        business.setOwnerId(USER_ID);
        business.setActive(true);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<Business> page =
                new org.springframework.data.domain.PageImpl<>(List.of(business), pageable, 1);
        when(businessRepository.findAll(pageable)).thenReturn(page);
        when(auth0Service.findEmailsByUserIds(List.of(USER_ID))).thenReturn(java.util.Map.of(USER_ID, EMAIL));

        org.springframework.data.domain.Page<AdminAccountListItemModel> result =
                adminAccountService.getAllAccounts(pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(EMAIL, result.getContent().get(0).getOwnerEmail());
        verify(auth0Service, never()).getUserEmailByUserId(anyString());
    }

    @Test
    void whenGetAllAccounts_andBatchLookupFails_thenDegradesEveryRowOnThatPageInsteadOfThrowing() {
        Business business = new Business();
        business.setBusinessId(new com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier(BUSINESS_ID));
        business.setName("Acme Signage");
        business.setOwnerId(USER_ID);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<Business> page =
                new org.springframework.data.domain.PageImpl<>(List.of(business), pageable, 1);
        when(businessRepository.findAll(pageable)).thenReturn(page);
        when(auth0Service.findEmailsByUserIds(List.of(USER_ID)))
                .thenThrow(new Auth0ServiceUnavailableException("boom", null));

        org.springframework.data.domain.Page<AdminAccountListItemModel> result =
                adminAccountService.getAllAccounts(pageable);

        assertEquals(1, result.getContent().size());
        assertNull(result.getContent().get(0).getOwnerEmail());
    }
}
