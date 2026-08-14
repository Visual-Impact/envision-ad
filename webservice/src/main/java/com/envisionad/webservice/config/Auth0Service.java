package com.envisionad.webservice.config;

import com.envisionad.webservice.config.exceptions.Auth0ServiceUnavailableException;
import com.envisionad.webservice.config.exceptions.Auth0UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class Auth0Service {

    private final RestTemplate restTemplate;

    @Value("${auth0.management.client-id}")
    private String managementClientId;

    @Value("${auth0.management.client-secret}")
    private String managementClientSecret;

    @Value("${auth0.management.audience}")
    private String managementAudience;

    @Value("${auth0.management.token-url}")
    private String managementTokenUrl;

    @Value("${auth0.management.base-url}")
    private String managementBaseUrl;

    @Value("${auth0.management.connection}")
    private String managementConnection;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Never shown to an admin or a client — see brief §2.3.1 for why this is throwaway. */
    private static final String PASSWORD_CHARSET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_=+";

    /** Holds a fetched Management API token alongside the instant it expires. */
    record CachedToken(String accessToken, Instant expiry) {
        boolean isValid() {
            return Instant.now().isBefore(expiry);
        }
    }

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    /** Guards the check-then-fetch-then-set refresh sequence against concurrent threads. */
    private final ReentrantLock tokenRefreshLock = new ReentrantLock();

    /** Safety buffer subtracted from expires_in to avoid using a token right at its boundary. */
    private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofSeconds(30);



    /**
     * Fetches the email for any user by their Auth0 user_id using the Management API
     * (server-to-server, no user JWT required). Always returns the latest email from Auth0.
     */
    public String getUserEmailByUserId(String userId) {
        String managementToken = getManagementApiToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(managementToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            java.net.URI uri = UriComponentsBuilder
                    .fromUriString(managementBaseUrl)
                    .pathSegment("api", "v2", "users", userId)
                    .build()
                    .toUri();

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    entity,
                    getMapTypeRef()
            );

            return extractEmail(response, userId);
        } catch (HttpClientErrorException.Unauthorized e) {
            // Token may have expired early (clock drift, early revocation). Invalidate the
            // cache under the lock so no other thread races to use the stale entry, then
            // retry once with a freshly-fetched token before giving up.
            tokenRefreshLock.lock();
            try {
                cachedToken.set(null);
            } finally {
                tokenRefreshLock.unlock();
            }
            HttpHeaders retryHeaders = new HttpHeaders();
            retryHeaders.setBearerAuth(getManagementApiToken());
            HttpEntity<String> retryEntity = new HttpEntity<>(retryHeaders);
            try {
                java.net.URI uri = UriComponentsBuilder
                        .fromUriString(managementBaseUrl)
                        .pathSegment("api", "v2", "users", userId)
                        .build()
                        .toUri();
                ResponseEntity<Map<String, Object>> retryResponse = restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        retryEntity,
                        getMapTypeRef()
                );
                return extractEmail(retryResponse, userId);
            } catch (RestClientException retryEx) {
                throw new Auth0ServiceUnavailableException(
                        "Failed to retrieve email for user '" + userId + "' from Auth0 Management API after token refresh", retryEx);
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new Auth0UserNotFoundException(userId, e);
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException(
                    "Failed to retrieve email for user '" + userId + "' from Auth0 Management API", e);
        }
    }

    /**
     * Creates a new Auth0 database-connection user (P5 admin account creation, brief
     * §2.3.1). The password is a securely-random throwaway string, immediately made
     * irrelevant by the password-change ticket the caller issues next (brief §2.4) —
     * it exists only because the Management API's POST /users requires *some* password
     * when the target connection enforces a password policy.
     */
    public String createUser(String email, String name) {
        Map<String, Object> body = Map.of(
                "email", email,
                "name", name,
                "connection", managementConnection,
                "password", generateThrowawayPassword(),
                "email_verified", true
        );
        try {
            ResponseEntity<Map<String, Object>> response =
                    exchangeWithTokenRetry(HttpMethod.POST, usersUri(), body, getMapTypeRef());
            Map<String, Object> responseBody = Objects.requireNonNull(response.getBody(),
                    "Auth0 Management API returned null body creating user '" + email + "'");
            String userId = (String) responseBody.get("user_id");
            if (userId == null || userId.isBlank())
                throw new Auth0ServiceUnavailableException(
                        "Auth0 Management API did not return a user_id for '" + email + "'", null);
            return userId;
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException("Failed to create Auth0 user for '" + email + "'", e);
        }
    }

    /**
     * Looks up an existing Auth0 user by email. Used both for the admin-creation
     * duplicate-email guard (brief FR 4.2.a) and the invitation-accept auto-provisioning
     * check (brief FR 3.2). Auth0's users-by-email endpoint returns 200 with an empty
     * array for "no match" — there is no 404 case to special-case here.
     */
    public Optional<String> findUserIdByEmail(String email) {
        URI uri = UriComponentsBuilder.fromUriString(managementBaseUrl)
                .pathSegment("api", "v2", "users-by-email")
                .queryParam("email", email)
                .build()
                .toUri();
        try {
            ResponseEntity<List<Map<String, Object>>> response = exchangeWithTokenRetry(
                    HttpMethod.GET, uri, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty())
                return Optional.empty();
            return Optional.ofNullable((String) body.get(0).get("user_id"));
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException("Failed to look up Auth0 user by email '" + email + "'", e);
        }
    }

    /**
     * Compensating action for FR 4.2.c: deletes an Auth0 user created moments ago when
     * the follow-up local DB write fails, so a DB validation failure never leaves an
     * orphaned Auth0 account behind.
     */
    public void deleteUser(String userId) {
        try {
            exchangeWithTokenRetry(HttpMethod.DELETE, usersUri(userId), null, new ParameterizedTypeReference<Void>() {});
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException("Failed to delete Auth0 user '" + userId + "'", e);
        }
    }

    /** Assigns Auth0 roles to a user (always BUSINESS_OWNER + flag-driven ones, brief §2.3.4). */
    public void assignRoles(String userId, List<String> roleIds) {
        URI uri = UriComponentsBuilder.fromUriString(managementBaseUrl)
                .pathSegment("api", "v2", "users", userId, "roles")
                .build()
                .toUri();
        Map<String, Object> body = Map.of("roles", roleIds);
        try {
            exchangeWithTokenRetry(HttpMethod.POST, uri, body, new ParameterizedTypeReference<Void>() {});
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException("Failed to assign roles to Auth0 user '" + userId + "'", e);
        }
    }

    /**
     * Issues a one-time password-change link (brief §2.4) — this is what makes "must
     * change password on first login" true by construction, since the user has no
     * usable password until they follow this link.
     */
    public String createPasswordChangeTicket(String userId, String resultUrl) {
        URI uri = UriComponentsBuilder.fromUriString(managementBaseUrl)
                .pathSegment("api", "v2", "tickets", "password-change")
                .build()
                .toUri();
        Map<String, Object> body = Map.of("user_id", userId, "result_url", resultUrl);
        try {
            ResponseEntity<Map<String, Object>> response =
                    exchangeWithTokenRetry(HttpMethod.POST, uri, body, getMapTypeRef());
            Map<String, Object> responseBody = Objects.requireNonNull(response.getBody(),
                    "Auth0 Management API returned null body creating a password-change ticket for user '" + userId + "'");
            String ticket = (String) responseBody.get("ticket");
            if (ticket == null || ticket.isBlank())
                throw new Auth0ServiceUnavailableException(
                        "Auth0 Management API did not return a ticket URL for user '" + userId + "'", null);
            return ticket;
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException(
                    "Failed to create a password-change ticket for Auth0 user '" + userId + "'", e);
        }
    }

    /** Mirrors business.active to Auth0's blocked flag (brief FR 4.4/5.5) — blocked users cannot log in. */
    public void setUserBlocked(String userId, boolean blocked) {
        Map<String, Object> body = Map.of("blocked", blocked);
        try {
            exchangeWithTokenRetry(HttpMethod.PATCH, usersUri(userId), body, getMapTypeRef());
        } catch (RestClientException e) {
            throw new Auth0ServiceUnavailableException(
                    "Failed to set blocked=" + blocked + " for Auth0 user '" + userId + "'", e);
        }
    }

    private URI usersUri(String... extraSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(managementBaseUrl)
                .pathSegment("api", "v2", "users");
        if (extraSegments.length > 0)
            builder.pathSegment(extraSegments);
        return builder.build().toUri();
    }

    private String generateThrowawayPassword() {
        StringBuilder password = new StringBuilder(32);
        for (int i = 0; i < 32; i++)
            password.append(PASSWORD_CHARSET.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARSET.length())));
        return password.toString();
    }

    /**
     * Shared token-fetch + single-401-retry wrapper for the write methods added in P5.
     * Mirrors getUserEmailByUserId's own retry logic without touching that
     * already-tested method — kept separate so this refactor carries zero risk to it.
     */
    private <T> ResponseEntity<T> exchangeWithTokenRetry(
            HttpMethod method, URI uri, Object requestBody, ParameterizedTypeReference<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getManagementApiToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplate.exchange(uri, method, new HttpEntity<>(requestBody, headers), responseType);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokenRefreshLock.lock();
            try {
                cachedToken.set(null);
            } finally {
                tokenRefreshLock.unlock();
            }
            HttpHeaders retryHeaders = new HttpHeaders();
            retryHeaders.setBearerAuth(getManagementApiToken());
            retryHeaders.setContentType(MediaType.APPLICATION_JSON);
            return restTemplate.exchange(uri, method, new HttpEntity<>(requestBody, retryHeaders), responseType);
        }
    }

    private String getManagementApiToken() {
        // Fast path — lock-free read for the common case of a still-valid token.
        CachedToken cached = cachedToken.get();
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }

        // Slow path — acquire lock so only one thread fetches a new token.
        tokenRefreshLock.lock();
        try {
            // Re-check after acquiring the lock; another thread may have already refreshed.
            cached = cachedToken.get();
            if (cached != null && cached.isValid()) {
                return cached.accessToken();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", managementClientId);
            body.add("client_secret", managementClientSecret);
            body.add("audience", managementAudience);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        managementTokenUrl,
                        HttpMethod.POST,
                        request,
                        getMapTypeRef()
                );

                Map<String, Object> responseBody = Objects.requireNonNull(response.getBody(), "Auth0 token endpoint returned null body");
                String accessToken = (String) responseBody.get("access_token");
                if (accessToken == null || accessToken.isBlank()) {
                    throw new Auth0ServiceUnavailableException(
                            "Auth0 token endpoint returned a response without a valid access_token", null);
                }
                Number expiresIn = (Number) responseBody.getOrDefault("expires_in", 3600);
                Instant expiry = Instant.now().plusSeconds(expiresIn.longValue()).minus(TOKEN_EXPIRY_BUFFER);
                cachedToken.set(new CachedToken(accessToken, expiry));
                return accessToken;
            } catch (RestClientException e) {
                throw new Auth0ServiceUnavailableException(
                        "Failed to obtain Auth0 Management API token", e);
            }
        } finally {
            tokenRefreshLock.unlock();
        }
    }


    private String extractEmail(ResponseEntity<Map<String, Object>> response, String userId) {
        Map<String, Object> body = Objects.requireNonNull(
                response.getBody(), "Auth0 Management API returned null body for user: " + userId);
        String email = (String) body.get("email");
        if (email == null || email.isBlank()) {
            throw new Auth0ServiceUnavailableException(
                    "Auth0 Management API returned no email for user: " + userId, null);
        }
        return email;
    }

    private ParameterizedTypeReference<Map<String, Object>> getMapTypeRef() {
        return new ParameterizedTypeReference<>() {};
    }
}