package com.envisionad.webservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WebConfigTest {

    /**
     * SimpleClientHttpRequestFactory (Spring's default) wraps HttpURLConnection, which
     * doesn't support PATCH — see Auth0Service.setUserBlocked. Guards against a future
     * revert to `new RestTemplate()` silently reintroducing that failure.
     */
    @Test
    void restTemplateUsesPatchCapableRequestFactory() {
        WebConfig config = new WebConfig(new ApplicationProperties("http://localhost:3000"));
        RestTemplate restTemplate = config.restTemplate();

        assertInstanceOf(JdkClientHttpRequestFactory.class, restTemplate.getRequestFactory());
    }
}
