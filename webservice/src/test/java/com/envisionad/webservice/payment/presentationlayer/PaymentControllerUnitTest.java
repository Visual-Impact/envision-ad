package com.envisionad.webservice.payment.presentationlayer;

import com.envisionad.webservice.payment.businesslogiclayer.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentController.
 * Tests payment-related endpoints through controller methods.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerUnitTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private StripeService stripeService;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "auth0|test-user-123")
                .claim("permissions", java.util.List.of("create:payment"))
                .build();
    }

    @Test
    void createConnectedAccount_shouldReturnAccountDetails() throws Exception {
        // Given
        String businessId = "biz-123";
        String returnUrl = "https://example.com/return";
        String refreshUrl = "https://example.com/refresh";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("businessId", businessId);
        requestBody.put("returnUrl", returnUrl);
        requestBody.put("refreshUrl", refreshUrl);

        Map<String, String> expectedResponse = new HashMap<>();
        expectedResponse.put("accountId", "acct_test123");
        expectedResponse.put("onboardingUrl", "https://connect.stripe.com/setup/test");

        when(stripeService.createConnectedAccountAndLink(eq(mockJwt), eq(businessId), eq(returnUrl), eq(refreshUrl)))
                .thenReturn(expectedResponse);

        // When
        ResponseEntity<Map<String, String>> response = paymentController.createConnectedAccount(mockJwt, requestBody);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
        verify(stripeService, times(1)).createConnectedAccountAndLink(mockJwt, businessId, returnUrl, refreshUrl);
    }

    @Test
    void getAccountStatus_shouldReturnStripeAccountStatus() {
        // Given
        String businessId = "biz-789";
        Map<String, Object> expectedStatus = new HashMap<>();
        expectedStatus.put("connected", true);
        expectedStatus.put("onboardingComplete", true);
        expectedStatus.put("chargesEnabled", true);
        expectedStatus.put("payoutsEnabled", true);
        expectedStatus.put("stripeAccountId", "acct_test123");

        when(stripeService.getAccountStatus(eq(mockJwt), eq(businessId)))
                .thenReturn(expectedStatus);

        // When
        ResponseEntity<Map<String, Object>> response = paymentController.getAccountStatus(mockJwt, businessId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedStatus, response.getBody());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("connected"));
        assertTrue((Boolean) response.getBody().get("onboardingComplete"));
        verify(stripeService, times(1)).getAccountStatus(mockJwt, businessId);
    }

    @Test
    void getAccountStatus_shouldHandleNotConnectedAccount() {
        // Given
        String businessId = "biz-no-stripe";
        Map<String, Object> expectedStatus = new HashMap<>();
        expectedStatus.put("connected", false);
        expectedStatus.put("onboardingComplete", false);
        expectedStatus.put("chargesEnabled", false);
        expectedStatus.put("payoutsEnabled", false);

        when(stripeService.getAccountStatus(eq(mockJwt), eq(businessId)))
                .thenReturn(expectedStatus);

        // When
        ResponseEntity<Map<String, Object>> response = paymentController.getAccountStatus(mockJwt, businessId);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("connected"));
        assertFalse((Boolean) response.getBody().get("onboardingComplete"));
    }

    @Test
    void getDashboardData_shouldReturnDashboardMetrics() throws Exception {
        // Given
        String businessId = "biz-dashboard";
        String period = "month";
        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("totalRevenue", 5000.00);
        expectedData.put("transactionCount", 25);
        expectedData.put("averageOrderValue", 200.00);

        when(stripeService.getDashboardData(eq(mockJwt), eq(businessId), eq(period)))
                .thenReturn(expectedData);

        // When
        ResponseEntity<Map<String, Object>> response = paymentController.getDashboardData(mockJwt, businessId, period);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedData, response.getBody());
        assertEquals(5000.00, response.getBody().get("totalRevenue"));
        assertEquals(25, response.getBody().get("transactionCount"));
        verify(stripeService, times(1)).getDashboardData(mockJwt, businessId, period);
    }
}

