package com.envisionad.webservice.business.presentationlayer;

import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.business.presentationlayer.models.BusinessRequestModel;
import com.envisionad.webservice.business.presentationlayer.models.InvitationRequestModel;
import com.envisionad.webservice.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BusinessControllerIntegrationTest extends BaseIntegrationTest {

    private final String BASE_URI_BUSINESSES = "/api/v1/businesses";

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VerificationRepository verificationRepository;

    @BeforeEach
    void setUp() {
        clearInvocations(emailService);
        verificationRepository.deleteAll();
        invitationRepository.deleteAll();
        employeeRepository.deleteAll();
        businessRepository.deleteAll();

        // --- Seed data (replaces data-h2.sql) ---
        Address addr1 = new Address("123 Baker St", "Montreal", "QC", "H3Z 2Y7", "Canada");
        Address addr2 = new Address("500 Tech Blvd", "Toronto", "ON", "M5V 2T6", "Canada");
        Address addr3 = new Address("789 Stanley Park Dr", "Vancouver", "BC", "V6G 3E2", "Canada");
        Address addr4 = new Address("404 Rocky View Rd", "Calgary", "AB", "T3K 5Y6", "Canada");
        Address addr5 = new Address("88 Parliament Hill", "Ottawa", "ON", "K1A 0A6", "Canada");

        Roles mediaOwnerAndAdvertiser = new Roles();
        mediaOwnerAndAdvertiser.setMediaOwner(true);
        mediaOwnerAndAdvertiser.setAdvertiser(true);

        Roles advertiserOnly = new Roles();
        advertiserOnly.setAdvertiser(true);

        Roles mediaOwnerOnly = new Roles();
        mediaOwnerOnly.setMediaOwner(true);

        Business b1 = new Business();
        b1.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b11"));
        b1.setName("Mom & Pop Bakery");
        b1.setOwnerId("auth0|6934e8515479d2b6d3cf7575");
        b1.setOrganizationSize(OrganizationSize.SMALL);
        b1.setAddress(addr1);
        b1.setRoles(mediaOwnerAndAdvertiser);
        b1.setVerified(true);

        Business b2 = new Business();
        b2.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"));
        b2.setName("TechGiant Solutions");
        b2.setOwnerId("auth0|696a89137cfdb558ea4a4a4a");
        b2.setOrganizationSize(OrganizationSize.ENTERPRISE);
        b2.setAddress(addr2);
        b2.setRoles(advertiserOnly);
        b2.setVerified(false);

        Business b3 = new Business();
        b3.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b33"));
        b3.setName("Lotus Yoga Studio");
        b3.setOwnerId("auth0|696a88eb347945897ef17093");
        b3.setOrganizationSize(OrganizationSize.LARGE);
        b3.setAddress(addr3);
        b3.setRoles(mediaOwnerOnly);
        b3.setVerified(false);

        Business b4 = new Business();
        b4.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b44"));
        b4.setName("Prairie Oil & Gas");
        b4.setOrganizationSize(OrganizationSize.ENTERPRISE);
        b4.setAddress(addr4);
        b4.setRoles(mediaOwnerOnly);
        b4.setVerified(false);

        Business b5 = new Business();
        b5.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b55"));
        b5.setName("Capital Consulting");
        b5.setOrganizationSize(OrganizationSize.MEDIUM);
        b5.setAddress(addr5);
        b5.setRoles(advertiserOnly);
        b5.setVerified(false);

        businessRepository.saveAll(List.of(b1, b2, b3, b4, b5));

        Employee emp1 = new Employee();
        emp1.setEmployeeId(new EmployeeIdentifier("94471b2f-8e87-4f47-bb14-604b8c4a32e6"));
        emp1.setUserId("auth0|6934e8515479d2b6d3cf7575");
        emp1.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b11"));

        Employee emp2 = new Employee();
        emp2.setEmployeeId(new EmployeeIdentifier("f0252067-78a2-41ea-ba88-34280aea7056"));
        emp2.setUserId("auth0|696a89137cfdb558ea4a4a4a");
        emp2.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"));

        Employee emp3 = new Employee();
        emp3.setEmployeeId(new EmployeeIdentifier("9c881832-3489-4518-b7a2-2e0ecdb659f2"));
        emp3.setUserId("auth0|695d67069dadc99900c81a0f");
        emp3.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"));

        Employee emp4 = new Employee();
        emp4.setEmployeeId(new EmployeeIdentifier("1f9b5afd-f206-447c-97b0-22002a4ff137"));
        emp4.setUserId("auth0|696a88eb347945897ef17093");
        emp4.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b33"));

        employeeRepository.saveAll(List.of(emp1, emp2, emp3, emp4));

        Invitation inv1 = new Invitation();
        inv1.setInvitationId(new InvitationIdentifier("6bb9b68a-a072-4f28-aaa0-601087d03401"));
        inv1.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"));
        inv1.setEmail("test@email.com");
        inv1.setToken("1dd9f712-d3e8-4714-a1dd-08d95012b122");
        inv1.setTimeExpires(LocalDateTime.now().plusHours(1));

        invitationRepository.save(inv1);

        Verification v1 = new Verification();
        v1.setVerificationId(new VerificationIdentifier("636e63e2-a3c0-4171-ac90-bfad8aeb6613"));
        v1.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b11"));
        v1.setStatus(VerificationStatus.DENIED);
        v1.setComments("Application denied due to invalid address entered");

        Verification v2 = new Verification();
        v2.setVerificationId(new VerificationIdentifier("75472797-b9e0-4e53-bdf8-81ffe57d9fa5"));
        v2.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b11"));
        v2.setStatus(VerificationStatus.APPROVED);

        Verification v3 = new Verification();
        v3.setVerificationId(new VerificationIdentifier("cf4dc890-d86c-48c4-9a8b-7705e0420da3"));
        v3.setBusinessId(new BusinessIdentifier("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"));
        v3.setStatus(VerificationStatus.PENDING);

        verificationRepository.saveAll(List.of(v1, v2, v3));

        // --- JWT mocks ---
        Jwt admin = Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a89377cfdb558ea4a4a61")
                .claim("scope", "read write")
                .claim("permissions", List.of(
                        "readAll:verification",
                        "update:verification",
                        "manage:accounts"
                ))
                .build();

        Jwt media = Jwt.withTokenValue("media-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a89137cfdb558ea4a4a4a")
                .claim("scope", "read write")
                .claim("permissions", List.of(
                        "create:media",
                        "update:media",
                        "update:business",
                        "read:employee",
                        "create:employee",
                        "delete:employee",
                        "read:verification",
                        "create:verification"
                ))
                .build();

        Jwt advertiser = Jwt.withTokenValue("advertiser-token")
                .header("alg", "none")
                .claim("sub", "auth0|696a88eb347945897ef17093")
                .claim("scope", "read write")
                .claim("permissions", List.of(
                        "read:campaign",
                        "create:campaign",
                        "update:campaign",
                        "update:business",
                        "read:employee",
                        "create:employee",
                        "delete:employee",
                        "read:verification",
                        "create:verification"
                ))
                .build();

        Jwt newUser = Jwt.withTokenValue("newUser-token")
                .header("alg", "none")
                .claim("sub", "auth0|696b10a00bba0a28c21d3829")
                .claim("scope", "read write")
                .build();

        when(jwtDecoder.decode("admin-token")).thenReturn(admin);
        when(jwtDecoder.decode("media-token")).thenReturn(media);
        when(jwtDecoder.decode("advertiser-token")).thenReturn(advertiser);
        when(jwtDecoder.decode("newUser-token")).thenReturn(newUser);
    }

    @Test
    void getAllBusinesses_ShouldReturnAllBusinesses() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URI_BUSINESSES)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("newUser-token"))
                .exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$.length()").isEqualTo(5);
    }

    @Test
    void createBusiness_withManageAccountsPermission_shouldPersistAndReturnBusiness() {
        // Arrange
        BusinessRequestModel requestModel = new BusinessRequestModel();
        requestModel.setName("Integration Business");
        requestModel.setOrganizationSize(OrganizationSize.MEDIUM);

        Address address = new Address();
        address.setStreet("Integration St");
        address.setCity("Integration City");
        address.setState("State");
        address.setZipCode("00000");
        address.setCountry("Country");
        requestModel.setAddress(address);

        Roles roles = new Roles();
        roles.setAdvertiser(true);
        roles.setMediaOwner(true);
        requestModel.setRoles(roles);

        // Act & Assert — P5 FR 1.4: createBusiness is admin-only (manage:accounts) now,
        // not "any authenticated user" — see createBusiness_withoutManageAccountsPermission_isForbidden.
        webTestClient.post()
                .uri(BASE_URI_BUSINESSES)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("admin-token"))
                .body(BodyInserters.fromValue(requestModel))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Integration Business")
                .jsonPath("$.organizationSize").isEqualTo("MEDIUM")
                .jsonPath("$.businessId").isNotEmpty()
                .jsonPath("$.address.street").isEqualTo("Integration St")
                .jsonPath("$.address.city").isEqualTo("Integration City");

        assertEquals(6, businessRepository.count());
    }

    @Test
    void createBusiness_withoutManageAccountsPermission_isForbidden() {
        BusinessRequestModel requestModel = new BusinessRequestModel();
        requestModel.setName("Should Not Be Created");
        requestModel.setOrganizationSize(OrganizationSize.MEDIUM);

        Address address = new Address();
        address.setStreet("Integration St");
        address.setCity("Integration City");
        address.setState("State");
        address.setZipCode("00000");
        address.setCountry("Country");
        requestModel.setAddress(address);

        Roles roles = new Roles();
        roles.setAdvertiser(true);
        requestModel.setRoles(roles);

        // newUser-token is authenticated but holds no permissions at all — this is the
        // exact case P5 closes off: an authenticated user with no manage:accounts can
        // no longer self-register a business.
        webTestClient.post()
                .uri(BASE_URI_BUSINESSES)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("newUser-token"))
                .body(BodyInserters.fromValue(requestModel))
                .exchange()
                .expectStatus().isForbidden();

        assertEquals(5, businessRepository.count());
    }

    @Test
    void getBusinessById_ShouldReturnOneBusiness() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b11";

        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/{businessId}", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.businessId").isEqualTo(businessId)
                .jsonPath("$.name").isEqualTo("Mom & Pop Bakery")
                .jsonPath("$.organizationSize").isEqualTo("SMALL");
    }

    @Test
    void getBusinessById_WithInvalidId_ShouldReturnNotFound() {
        String invalidBusinessId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/{businessId}", invalidBusinessId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateBusinessById_ShouldUpdateAndReturnBusiness() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        // Arrange
        BusinessRequestModel requestModel = new BusinessRequestModel();
        requestModel.setName("Integration Business");
        requestModel.setOrganizationSize(OrganizationSize.LARGE);

        Address address = new Address();
        address.setStreet("Integration St");
        address.setCity("Integration City");
        address.setState("State");
        address.setZipCode("00000");
        address.setCountry("Country");
        requestModel.setAddress(address);

        Roles roles = new Roles();
        roles.setAdvertiser(true);
        roles.setMediaOwner(true);
        requestModel.setRoles(roles);

        // Act & Assert
        webTestClient.put()
                .uri(BASE_URI_BUSINESSES + "/{businessId}", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .body(BodyInserters.fromValue(requestModel))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.businessId").isEqualTo(businessId)
                .jsonPath("$.name").isEqualTo("Integration Business")
                .jsonPath("$.organizationSize").isEqualTo("LARGE")
                .jsonPath("$.address.street").isEqualTo("Integration St")
                .jsonPath("$.address.city").isEqualTo("Integration City")
                .jsonPath("$.address.state").isEqualTo("State");

        assertEquals(5, businessRepository.count());
    }

    @Test
    void approveBusinessVerificationByBusinessIdAndVerificationId_ShouldReturnVerification() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
        String verificationId = "cf4dc890-d86c-48c4-9a8b-7705e0420da3";

        // Act & Assert
        webTestClient.patch()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/verifications/{verificationId}/approve", businessId, verificationId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("admin-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.verificationId").isEqualTo(verificationId)
                .jsonPath("$.businessId").isEqualTo(businessId)
                .jsonPath("$.status").isEqualTo(VerificationStatus.APPROVED)
                .jsonPath("$.comments").isEmpty();

        assertEquals(3, verificationRepository.count());
    }

    @Test
    void denyBusinessVerificationByBusinessIdAndVerificationId_ShouldReturnVerification() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
        String verificationId = "cf4dc890-d86c-48c4-9a8b-7705e0420da3";
        String comment = "Denied because of missing address.";

        // Act & Assert
        webTestClient.patch()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/verifications/{verificationId}/deny", businessId, verificationId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("admin-token"))
                .body(BodyInserters.fromValue(comment))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.verificationId").isEqualTo(verificationId)
                .jsonPath("$.businessId").isEqualTo(businessId)
                .jsonPath("$.status").isEqualTo(VerificationStatus.DENIED)
                .jsonPath("$.comments").isEqualTo(comment);

        assertEquals(3, verificationRepository.count());
    }

    @Test
    void requestBusinessVerification_ShouldReturnVerification() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b33";

        // Act & Assert
        webTestClient.post()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/verifications", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("advertiser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.businessId").isEqualTo(businessId)
                .jsonPath("$.status").isEqualTo(VerificationStatus.PENDING);

        assertEquals(4, verificationRepository.count());
    }

    @Test
    void getAllBusinessVerifications_ShouldReturnOneVerifications() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        // Act & Assert
        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/verifications", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void getAllPendingVerifications_ShouldReturnOneVerification() {
        // Act & Assert
        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/verifications")
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("admin-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void getAllBusinessInvitations_ShouldReturnAllInvitations() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
        long initialInvitationCount = invitationRepository.findAllByBusinessId_BusinessId(businessId).size();

        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/invites", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()").isEqualTo(initialInvitationCount);
    }

    @Test
    void createInvitation_ShouldPersistAndReturnInvitation() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
        long initialInvitationCount = invitationRepository.count();

        InvitationRequestModel invitationRequest = new InvitationRequestModel();
        invitationRequest.setEmail("newemployee@test.com");

        webTestClient.post()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/invites", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .body(BodyInserters.fromValue(invitationRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.email").isEqualTo("newemployee@test.com")
                .jsonPath("$.invitationId").isNotEmpty();

        assertEquals(initialInvitationCount + 1, invitationRepository.count());
        verify(emailService, times(1)).sendSimpleEmail(anyString(), anyString(), anyString());
    }

    @Test
    void cancelInvitation_ShouldRemoveInvitation() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        // First create an invitation
        InvitationRequestModel invitationRequest = new InvitationRequestModel();
        invitationRequest.setEmail("todelete@test.com");

        webTestClient.post()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/invites", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .body(BodyInserters.fromValue(invitationRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.invitationId").isNotEmpty()
                .jsonPath("$.email").isEqualTo("todelete@test.com");

        long countBeforeDelete = invitationRepository.count();

        // Get the invitation ID from the repository
        Invitation invitation = invitationRepository.findAll().stream()
                .filter(inv -> inv.getEmail().equals("todelete@test.com"))
                .findFirst()
                .orElseThrow();

        // Now delete it
        webTestClient.delete()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/invites/{invitationId}",
                        businessId, invitation.getInvitationId().getInvitationId())
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isNoContent();

        assertEquals(countBeforeDelete - 1, invitationRepository.count());
    }

    @Test
    void getAllBusinessEmployees_ShouldReturnAllEmployees() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";
        long employeeCount = employeeRepository.findAllByBusinessId_BusinessId(businessId).size();

        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/employees", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()").isEqualTo(employeeCount);
    }

    @Test
    void addEmployeeToBusiness_WithValidToken_ShouldAddEmployee() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        // Create an invitation first
        InvitationRequestModel invitationRequest = new InvitationRequestModel();
        invitationRequest.setEmail("newemployee@test.com");

        webTestClient.post()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/invites", businessId)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .body(BodyInserters.fromValue(invitationRequest))
                .exchange()
                .expectStatus().isCreated();

        // Get the token
        Invitation invitation = invitationRepository.findAll().stream()
                .filter(inv -> inv.getEmail().equals("newemployee@test.com"))
                .findFirst()
                .orElseThrow();

        String token = invitation.getToken();
        long employeeCountBefore = employeeRepository.count();

        // Add employee using token — P5 FR 3.2: response is now the wrapped
        // InvitationAcceptResponseModel ({status, employee}), not a bare EmployeeResponseModel.
        // Auth0-touching branches (LOGIN_REQUIRED/PROVISIONED, no JWT) are covered by
        // BusinessServiceUnitTest instead, same constraint as every other Auth0Service caller.
        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_BUSINESSES + "/{businessId}/employees")
                        .queryParam("token", token)
                        .build(businessId))
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("newUser-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED")
                .jsonPath("$.employee.employeeId").isNotEmpty();

        assertEquals(employeeCountBefore + 1, employeeRepository.count());
    }

    @Test
    void addEmployeeToBusiness_WithoutJwt_isAllowedThrough() {
        // P5 FR 3.2: the endpoint is no longer @PreAuthorize("isAuthenticated()") — an
        // anonymous caller with an invalid token still gets a normal 404, not a 401/403,
        // confirming the security gate itself was actually lifted (not just that this
        // particular request happens to succeed).
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URI_BUSINESSES + "/{businessId}/employees")
                        .queryParam("token", "not-a-real-token")
                        .build(businessId))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void removeEmployeeFromBusiness_ShouldRemoveEmployee() {
        String businessId = "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22";

        // Get an existing employee
        List<Employee> employees = employeeRepository.findAllByBusinessId_BusinessId(businessId);
        assertFalse(employees.isEmpty(), "Should have at least one employee");

        Employee employeeToRemove = employees.getLast();
        String employeeId = employeeToRemove.getEmployeeId().getEmployeeId();
        long employeeCountBefore = employeeRepository.count();

        webTestClient.delete()
                .uri(BASE_URI_BUSINESSES + "/{businessId}/employees/{employeeId}",
                        businessId, employeeId)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isNoContent();

        assertEquals(employeeCountBefore - 1, employeeRepository.count());
    }

    @Test
    void getBusinessByUserId_ShouldReturnBusiness() {
        String userId = "auth0|696a89137cfdb558ea4a4a4a";

        webTestClient.get()
                .uri(BASE_URI_BUSINESSES + "/employee/{userId}", userId)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth("media-token"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.businessId").isNotEmpty()
                .jsonPath("$.name").isNotEmpty();
    }
}
