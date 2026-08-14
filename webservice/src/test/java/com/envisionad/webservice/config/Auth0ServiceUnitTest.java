package com.envisionad.webservice.config;

import com.envisionad.webservice.config.exceptions.Auth0ServiceUnavailableException;
import com.envisionad.webservice.config.exceptions.Auth0UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Auth0ServiceUnitTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private Auth0Service auth0Service;

    private static final String BASE_URL      = "https://dev-test.auth0.com/";
    private static final String CLIENT_ID     = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String AUDIENCE      = "https://dev-test.auth0.com/api/v2/";
    private static final String TOKEN_URL     = BASE_URL + "oauth/token";
    private static final String VALID_TOKEN   = "mocked-management-token";
    private static final String USER_ID       = "auth0|abc123";
    private static final String USER_EMAIL    = "advertiser@example.com";

    /** The URI that Auth0Service actually builds via UriComponentsBuilder.pathSegment(). */
    private static URI userUri() {
        return UriComponentsBuilder
                .fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users", Auth0ServiceUnitTest.USER_ID)
                .build()
                .toUri();
    }

    private static final String CONNECTION = "Username-Password-Authentication";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auth0Service, "managementTokenUrl",    TOKEN_URL);
        ReflectionTestUtils.setField(auth0Service, "managementBaseUrl",     BASE_URL);
        ReflectionTestUtils.setField(auth0Service, "managementClientId",    CLIENT_ID);
        ReflectionTestUtils.setField(auth0Service, "managementClientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(auth0Service, "managementAudience",    AUDIENCE);
        ReflectionTestUtils.setField(auth0Service, "managementConnection",  CONNECTION);
        // Clear the cached token between tests so each test starts fresh
        ReflectionTestUtils.setField(auth0Service, "cachedToken",
                new java.util.concurrent.atomic.AtomicReference<>());
    }

    private static URI usersUri() {
        return UriComponentsBuilder.fromUriString(BASE_URL).pathSegment("api", "v2", "users").build().toUri();
    }

    private static URI userUriFor(String userId) {
        return UriComponentsBuilder.fromUriString(BASE_URL).pathSegment("api", "v2", "users", userId).build().toUri();
    }

    // -------------------------------------------------------------------------
    // Helper stubs
    // -------------------------------------------------------------------------

    private void stubTokenEndpoint() {
        Map<String, Object> tokenBody = Map.of("access_token", Auth0ServiceUnitTest.VALID_TOKEN, "expires_in", 86400);
        when(restTemplate.exchange(
                eq(TOKEN_URL),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(tokenBody, HttpStatus.OK));
    }

    private void stubUserEndpoint() {
        Map<String, Object> userBody = Map.of("email", Auth0ServiceUnitTest.USER_EMAIL, "user_id", Auth0ServiceUnitTest.USER_ID);
        when(restTemplate.exchange(
                eq(userUri()),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(userBody, HttpStatus.OK));
    }

    // =========================================================================
    // getUserEmailByUserId — happy path
    // =========================================================================

    @Test
    void whenGetUserEmailByUserId_withValidUserId_thenReturnsEmail() {
        // Arrange
        stubTokenEndpoint();
        stubUserEndpoint();

        // Act
        String result = auth0Service.getUserEmailByUserId(USER_ID);

        // Assert
        assertEquals(USER_EMAIL, result);
        verify(restTemplate, times(1)).exchange(eq(TOKEN_URL), eq(HttpMethod.POST), any(), ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any());
        verify(restTemplate, times(1)).exchange(eq(userUri()), eq(HttpMethod.GET), any(), ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any());
    }

    // =========================================================================
    // getUserEmailByUserId — 404 → Auth0UserNotFoundException
    // =========================================================================

    @Test
    void whenGetUserEmailByUserId_andUserNotFound_thenThrowsAuth0UserNotFoundException() {
        // Arrange
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUri()),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        // Act & Assert
        Auth0UserNotFoundException ex = assertThrows(
                Auth0UserNotFoundException.class,
                () -> auth0Service.getUserEmailByUserId(USER_ID)
        );
        assertTrue(ex.getMessage().contains(USER_ID));
    }

    // =========================================================================
    // getUserEmailByUserId — 401 → clears cache, retries, then throws
    // =========================================================================

    @Test
    void whenGetUserEmailByUserId_andUnauthorized_thenThrowsAuth0ServiceUnavailableException() {
        // Arrange — first token fetch succeeds; both user-endpoint calls (initial + retry) get 401
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUri()),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        // Act & Assert
        Auth0ServiceUnavailableException ex = assertThrows(
                Auth0ServiceUnavailableException.class,
                () -> auth0Service.getUserEmailByUserId(USER_ID)
        );
        assertTrue(ex.getMessage().contains(USER_ID));
        // The retry path wraps the second RestClientException as the cause
        assertNotNull(ex.getCause());
    }

    // =========================================================================
    // getUserEmailByUserId — network failure → Auth0ServiceUnavailableException
    // =========================================================================

    @Test
    void whenGetUserEmailByUserId_andNetworkFailure_thenThrowsAuth0ServiceUnavailableException() {
        // Arrange
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUri()),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(new ResourceAccessException("Connection refused"));

        // Act & Assert
        Auth0ServiceUnavailableException ex = assertThrows(
                Auth0ServiceUnavailableException.class,
                () -> auth0Service.getUserEmailByUserId(USER_ID)
        );
        assertInstanceOf(ResourceAccessException.class, ex.getCause());
    }

    // =========================================================================
    // getManagementApiToken — token endpoint failure → Auth0ServiceUnavailableException
    // =========================================================================

    @Test
    void whenGetManagementApiToken_andTokenEndpointFails_thenThrowsAuth0ServiceUnavailableException() {
        // Arrange
        when(restTemplate.exchange(
                eq(TOKEN_URL),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null));

        // Act & Assert
        Auth0ServiceUnavailableException ex = assertThrows(
                Auth0ServiceUnavailableException.class,
                () -> auth0Service.getUserEmailByUserId(USER_ID)
        );
        assertTrue(ex.getMessage().contains("Failed to obtain Auth0 Management API token"));
        assertInstanceOf(HttpClientErrorException.class, ex.getCause());
    }

    // =========================================================================
    // Token caching — cached token is reused across multiple calls
    // =========================================================================

    @Test
    void whenGetUserEmailByUserId_calledMultipleTimes_thenTokenEndpointCalledOnlyOnce() {
        // Arrange
        stubTokenEndpoint();
        stubUserEndpoint();

        // Act
        auth0Service.getUserEmailByUserId(USER_ID);
        auth0Service.getUserEmailByUserId(USER_ID);
        auth0Service.getUserEmailByUserId(USER_ID);

        // Assert — token fetched exactly once despite three email lookups
        verify(restTemplate, times(1)).exchange(
                eq(TOKEN_URL),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        );
        verify(restTemplate, times(3)).exchange(
                eq(userUri()),
                eq(HttpMethod.GET),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        );
    }

    // =========================================================================
    // Token caching — expired token triggers a fresh fetch
    // =========================================================================

    @Test
    void whenCachedTokenIsExpired_thenFetchesNewToken() throws Exception {
        // Arrange — pre-load an already-expired CachedToken into the cache
        Class<?> cachedTokenClass = Class.forName(
                "com.envisionad.webservice.config.Auth0Service$CachedToken");
        java.lang.reflect.Constructor<?> ctor =
                cachedTokenClass.getDeclaredConstructor(String.class, Instant.class);
        ctor.setAccessible(true);
        Object expiredToken = ctor.newInstance("expired-token", Instant.now().minusSeconds(60));

        var expiredCache = new java.util.concurrent.atomic.AtomicReference<>(expiredToken);
        ReflectionTestUtils.setField(auth0Service, "cachedToken", expiredCache);

        stubTokenEndpoint();
        stubUserEndpoint();

        // Act
        String result = auth0Service.getUserEmailByUserId(USER_ID);

        // Assert — a fresh token was fetched and the correct email was returned
        assertEquals(USER_EMAIL, result);
        verify(restTemplate, times(1)).exchange(
                eq(TOKEN_URL),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        );
    }


    // =========================================================================
    // Token caching — valid cached token is not refreshed
    // =========================================================================

    @Test
    void whenCachedTokenIsStillValid_thenTokenEndpointIsNotCalled() throws Exception {
        // Arrange — pre-load a still-valid CachedToken (expires in 22 hours)
        Class<?> cachedTokenClass = Class.forName(
                "com.envisionad.webservice.config.Auth0Service$CachedToken");
        java.lang.reflect.Constructor<?> ctor =
                cachedTokenClass.getDeclaredConstructor(String.class, Instant.class);
        ctor.setAccessible(true);
        Object validToken = ctor.newInstance(VALID_TOKEN, Instant.now().plusSeconds(22 * 3600));

        var validCache = new java.util.concurrent.atomic.AtomicReference<>(validToken);
        ReflectionTestUtils.setField(auth0Service, "cachedToken", validCache);

        stubUserEndpoint();

        // Act
        String result = auth0Service.getUserEmailByUserId(USER_ID);

        // Assert — token endpoint was never hit
        assertEquals(USER_EMAIL, result);
        verify(restTemplate, never()).exchange(
                eq(TOKEN_URL),
                eq(HttpMethod.POST),
                any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        );
    }

    // =========================================================================
    // createUser (P5)
    // =========================================================================

    @Test
    void whenCreateUser_withValidInput_thenReturnsUserId() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(usersUri()), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(Map.of("user_id", USER_ID), HttpStatus.CREATED));

        String result = auth0Service.createUser(USER_EMAIL, "Jane Doe");

        assertEquals(USER_ID, result);
    }

    @Test
    void whenCreateUser_andNoUserIdReturned_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(usersUri()), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.CREATED));

        assertThrows(Auth0ServiceUnavailableException.class,
                () -> auth0Service.createUser(USER_EMAIL, "Jane Doe"));
    }

    @Test
    void whenCreateUser_andAuth0Rejects_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(usersUri()), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        assertThrows(Auth0ServiceUnavailableException.class,
                () -> auth0Service.createUser(USER_EMAIL, "Jane Doe"));
    }

    // =========================================================================
    // findUserIdByEmail (P5)
    // =========================================================================

    @Test
    void whenFindUserIdByEmail_andUserExists_thenReturnsUserId() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users-by-email").queryParam("email", USER_EMAIL).build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.GET), any(),
                ArgumentMatchers.<ParameterizedTypeReference<List<Map<String, Object>>>>any()
        )).thenReturn(new ResponseEntity<>(List.of(Map.of("user_id", USER_ID)), HttpStatus.OK));

        Optional<String> result = auth0Service.findUserIdByEmail(USER_EMAIL);

        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get());
    }

    @Test
    void whenFindUserIdByEmail_andNoMatch_thenReturnsEmpty() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users-by-email").queryParam("email", USER_EMAIL).build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.GET), any(),
                ArgumentMatchers.<ParameterizedTypeReference<List<Map<String, Object>>>>any()
        )).thenReturn(new ResponseEntity<>(List.of(), HttpStatus.OK));

        assertTrue(auth0Service.findUserIdByEmail(USER_EMAIL).isEmpty());
    }

    @Test
    void whenFindUserIdByEmail_andNetworkFailure_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users-by-email").queryParam("email", USER_EMAIL).build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.GET), any(),
                ArgumentMatchers.<ParameterizedTypeReference<List<Map<String, Object>>>>any()
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(Auth0ServiceUnavailableException.class, () -> auth0Service.findUserIdByEmail(USER_EMAIL));
    }

    // =========================================================================
    // deleteUser (P5)
    // =========================================================================

    @Test
    void whenDeleteUser_thenSucceeds() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUriFor(USER_ID)), eq(HttpMethod.DELETE), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any()
        )).thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        assertDoesNotThrow(() -> auth0Service.deleteUser(USER_ID));
    }

    @Test
    void whenDeleteUser_andAuth0Fails_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUriFor(USER_ID)), eq(HttpMethod.DELETE), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any()
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(Auth0ServiceUnavailableException.class, () -> auth0Service.deleteUser(USER_ID));
    }

    // =========================================================================
    // assignRoles (P5)
    // =========================================================================

    @Test
    void whenAssignRoles_thenSucceeds() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users", USER_ID, "roles").build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any()
        )).thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        assertDoesNotThrow(() -> auth0Service.assignRoles(USER_ID, List.of(Auth0Roles.BUSINESS_OWNER)));
    }

    @Test
    void whenAssignRoles_andAuth0Fails_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users", USER_ID, "roles").build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any()
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(Auth0ServiceUnavailableException.class,
                () -> auth0Service.assignRoles(USER_ID, List.of(Auth0Roles.BUSINESS_OWNER)));
    }

    // =========================================================================
    // createPasswordChangeTicket (P5)
    // =========================================================================

    @Test
    void whenCreatePasswordChangeTicket_thenReturnsTicketUrl() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "tickets", "password-change").build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(Map.of("ticket", "https://auth0.example.com/ticket/abc"), HttpStatus.CREATED));

        String result = auth0Service.createPasswordChangeTicket(USER_ID, "https://app.example.com/auth/login");

        assertEquals("https://auth0.example.com/ticket/abc", result);
    }

    @Test
    void whenCreatePasswordChangeTicket_andNoTicketReturned_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "tickets", "password-change").build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.CREATED));

        assertThrows(Auth0ServiceUnavailableException.class,
                () -> auth0Service.createPasswordChangeTicket(USER_ID, "https://app.example.com/auth/login"));
    }

    // =========================================================================
    // setUserBlocked (P5)
    // =========================================================================

    @Test
    void whenSetUserBlocked_thenSucceeds() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUriFor(USER_ID)), eq(HttpMethod.PATCH), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.OK));

        assertDoesNotThrow(() -> auth0Service.setUserBlocked(USER_ID, true));
    }

    @Test
    void whenSetUserBlocked_andAuth0Fails_thenThrowsAuth0ServiceUnavailableException() {
        stubTokenEndpoint();
        when(restTemplate.exchange(
                eq(userUriFor(USER_ID)), eq(HttpMethod.PATCH), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(Auth0ServiceUnavailableException.class, () -> auth0Service.setUserBlocked(USER_ID, false));
    }

    // =========================================================================
    // exchangeWithTokenRetry — 401 triggers exactly one retry (shared by all P5 writes)
    // =========================================================================

    @Test
    void whenAssignRoles_andFirstAttemptUnauthorized_thenRetriesOnceAndSucceeds() {
        stubTokenEndpoint();
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("api", "v2", "users", USER_ID, "roles").build().toUri();
        when(restTemplate.exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any()
        )).thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null))
                .thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        assertDoesNotThrow(() -> auth0Service.assignRoles(USER_ID, List.of(Auth0Roles.BUSINESS_OWNER)));

        verify(restTemplate, times(2)).exchange(
                eq(uri), eq(HttpMethod.POST), any(),
                ArgumentMatchers.<ParameterizedTypeReference<Void>>any());
    }
}

