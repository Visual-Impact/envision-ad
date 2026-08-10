package com.envisionad.webservice.advertisement.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

    @Test
    void invalidAdTypeException_messageContainsProvidedType() {
        var ex = new InvalidAdTypeException("gif");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("gif"));
        assertTrue(ex.getMessage().contains("video") && ex.getMessage().contains("image"));
    }

    @Test
    void adCampaignNotFoundException_messageContainsCampaignId_andHasResponseStatusNotFound() {
        var ex = new AdCampaignNotFoundException("abc-123");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("abc-123"));

        ResponseStatus rs = ex.getClass().getAnnotation(ResponseStatus.class);
        assertNotNull(rs, "@ResponseStatus should be present on AdCampaignNotFoundException");
        assertEquals(HttpStatus.NOT_FOUND, rs.code());
    }

    @Test
    void adNotFoundException_messageContainsAdId_andHasResponseStatusNotFound() {
        var ex = new AdNotFoundException("ad-1");
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("ad-1"));

        ResponseStatus rs = ex.getClass().getAnnotation(ResponseStatus.class);
        assertNotNull(rs, "@ResponseStatus should be present on AdNotFoundException");
        assertEquals(HttpStatus.NOT_FOUND, rs.code());
    }
}

