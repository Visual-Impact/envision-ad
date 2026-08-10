package com.envisionad.webservice.media.BusinessLayer;

import com.envisionad.webservice.media.exceptions.GeocodingServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "rawtypes", "unchecked" })
class GeocodingServiceImplTest {

    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private UriBuilder uriBuilder;

    private GeocodingServiceImpl geocodingService;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        geocodingService = new GeocodingServiceImpl(webClientBuilder);
    }

    @Test
    void geocodeAddress_ValidAddress_ReturnsJson() {
        String jsonResponse = "[{\"lat\":\"45.5017\", \"lon\":\"-73.5673\"}]";

        // Use unchecked cast to avoid compilation error with raw/generic mismatch if
        // easy, or just suppress warnings
        // Actually, let's just use raw types or wildcards properly if possible.
        // WebClient generics are complex to mock perfectly.
        // Let's use simpler mocking if possible or suppress warnings.
        // Given the lints, I will suppress warnings for the test class or keys.

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        Optional<String> result = geocodingService.geocodeAddress("Montreal, QC");

        assertTrue(result.isPresent());
        assertEquals(jsonResponse, result.get());
    }

    @Test
    void geocodeAddress_BuildsExpectedUriAndQueryParams() throws Exception {
        String jsonResponse = "[{\"lat\":\"45.5017\", \"lon\":\"-73.5673\"}]";
        String address = "123 Main St, Montreal";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenAnswer(invocation -> {
            Function<UriBuilder, URI> uriFunction = invocation.getArgument(0);
            uriFunction.apply(uriBuilder);
            return requestHeadersSpec;
        });
        when(uriBuilder.path("/search")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("q", address)).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("format", "json")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("addressdetails", 1)).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("limit", 1)).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(new URI("https://nominatim.openstreetmap.org/search"));
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        Optional<String> result = geocodingService.geocodeAddress(address);

        assertTrue(result.isPresent());
        verify(uriBuilder).path("/search");
        verify(uriBuilder).queryParam("q", address);
        verify(uriBuilder).queryParam("format", "json");
        verify(uriBuilder).queryParam("addressdetails", 1);
        verify(uriBuilder).queryParam("limit", 1);
        verify(uriBuilder).build();
    }

    @Test
    void geocodeAddress_InvalidAddress_ReturnsEmpty() {
        String jsonResponse = "[]";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        Optional<String> result = geocodingService.geocodeAddress("Invalid Address");

        assertFalse(result.isPresent());
    }

    @Test
    void geocodeAddress_NullResponse_ReturnsEmpty() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.justOrEmpty((String) null));

        Optional<String> result = geocodingService.geocodeAddress("No Response Address");

        assertTrue(result.isEmpty());
    }

    @Test
    void geocodeAddress_ApiError_ThrowsServiceUnavailableException() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("API Error")));

        GeocodingServiceUnavailableException exception = assertThrows(GeocodingServiceUnavailableException.class,
                () -> geocodingService.geocodeAddress("Montreal, QC"));
        assertEquals("Address validation service is temporarily unavailable.", exception.getMessage());
    }

    @Test
    void geocodeAddress_Timeout_ThrowsServiceUnavailableException() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new TimeoutException("timeout")));

        GeocodingServiceUnavailableException exception = assertThrows(GeocodingServiceUnavailableException.class,
                () -> geocodingService.geocodeAddress("Montreal, QC"));
        assertEquals("Address validation service is temporarily unavailable.", exception.getMessage());
    }

    @Test
    void geocodeStructuredAddress_ValidAddress_ReturnsJson() {
        String jsonResponse = "[{\"lat\":\"45.5017\", \"lon\":\"-73.5673\"}]";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        Optional<String> result = geocodingService.geocodeStructuredAddress(
                "3040 Sherbrooke St W", "Montreal", "QC", "Canada", "H3Z 1A4");

        assertTrue(result.isPresent());
        assertEquals(jsonResponse, result.get());
    }

    @Test
    void geocodeStructuredAddress_BuildsExpectedUriAndQueryParams() throws Exception {
        String jsonResponse = "[{\"lat\":\"45.5017\", \"lon\":\"-73.5673\"}]";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenAnswer(invocation -> {
            Function<UriBuilder, URI> uriFunction = invocation.getArgument(0);
            uriFunction.apply(uriBuilder);
            return requestHeadersSpec;
        });
        when(uriBuilder.path("/search")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("street", "3040 Sherbrooke St W")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("city", "Montreal")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("state", "QC")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("country", "Canada")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("postalcode", "H3Z 1A4")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("format", "json")).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("addressdetails", 1)).thenReturn(uriBuilder);
        when(uriBuilder.queryParam("limit", 1)).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(new URI("https://nominatim.openstreetmap.org/search"));
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));

        Optional<String> result = geocodingService.geocodeStructuredAddress(
                "3040 Sherbrooke St W", "Montreal", "QC", "Canada", "H3Z 1A4");

        assertTrue(result.isPresent());
        verify(uriBuilder).queryParam("street", "3040 Sherbrooke St W");
        verify(uriBuilder).queryParam("city", "Montreal");
        verify(uriBuilder).queryParam("state", "QC");
        verify(uriBuilder).queryParam("country", "Canada");
        verify(uriBuilder).queryParam("postalcode", "H3Z 1A4");
    }

    @Test
    void geocodeStructuredAddress_InvalidAddress_ReturnsEmpty() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("[]"));

        Optional<String> result = geocodingService.geocodeStructuredAddress(
                "Nowhere", "Nowhere", "Nowhere", "Nowhere", "00000");

        assertFalse(result.isPresent());
    }

    @Test
    void geocodeStructuredAddress_ApiError_ThrowsServiceUnavailableException() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("API Error")));

        GeocodingServiceUnavailableException exception = assertThrows(GeocodingServiceUnavailableException.class,
                () -> geocodingService.geocodeStructuredAddress("St", "City", "State", "Country", "12345"));
        assertEquals("Address validation service is temporarily unavailable.", exception.getMessage());
    }

    @Test
    void getCached_WhenEntryMissing_ReturnsNull() throws Exception {
        Method getCached = GeocodingServiceImpl.class.getDeclaredMethod("getCached", String.class);
        getCached.setAccessible(true);

        Object result = getCached.invoke(geocodingService, "missing-key");

        assertNull(result);
    }

    @Test
    void getCached_WhenEntryExpired_RemovesEntryAndReturnsNull() throws Exception {
        Field cacheField = GeocodingServiceImpl.class.getDeclaredField("geocodingCache");
        cacheField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) cacheField.get(geocodingService);

        Class<?> cacheEntryClass = Class.forName(
                "com.envisionad.webservice.media.BusinessLayer.GeocodingServiceImpl$CacheEntry"
        );
        Constructor<?> cacheEntryCtor = cacheEntryClass.getDeclaredConstructor(Optional.class, long.class);
        cacheEntryCtor.setAccessible(true);
        Object expiredEntry = cacheEntryCtor.newInstance(
                Optional.of("{\"lat\":\"45.0\",\"lon\":\"-73.0\"}"),
                System.currentTimeMillis() - 1
        );

        String cacheKey = "expired-key";
        cache.put(cacheKey, expiredEntry);

        Method getCached = GeocodingServiceImpl.class.getDeclaredMethod("getCached", String.class);
        getCached.setAccessible(true);

        Object result = getCached.invoke(geocodingService, cacheKey);

        assertNull(result);
        assertFalse(cache.containsKey(cacheKey));
    }
}
