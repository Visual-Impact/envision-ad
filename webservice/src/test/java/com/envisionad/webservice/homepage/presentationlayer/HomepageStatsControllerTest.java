package com.envisionad.webservice.homepage.presentationlayer;

import com.envisionad.webservice.config.ApplicationProperties;
import com.envisionad.webservice.config.AuthenticationErrorHandler;
import com.envisionad.webservice.config.SecurityConfig;
import com.envisionad.webservice.homepage.businesslogiclayer.HomepageStatsService;
import com.envisionad.webservice.homepage.presentationlayer.models.HomepageStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(SecurityConfig.class)
@WebMvcTest(HomepageStatsController.class)
class HomepageStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomepageStatsService homepageStatsService;

    @MockitoBean
    private ApplicationProperties applicationProperties;

    @MockitoBean
    private AuthenticationErrorHandler authenticationErrorHandler;

    @Test
    void getStats_returnsExpectedJson() throws Exception {
        when(homepageStatsService.getStats()).thenReturn(
                new HomepageStatsResponse(21L, 6L, 8L, 411600L)
        );

        mockMvc.perform(get("/api/v1/public/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.activeScreens").value(21))
                .andExpect(jsonPath("$.citiesCovered").value(6))
                .andExpect(jsonPath("$.venueTypes").value(8))
                .andExpect(jsonPath("$.monthlyBroadcasts").value(411600));
    }

    @Test
    void getStats_isPublicAndRequiresNoAuth() throws Exception {
        when(homepageStatsService.getStats()).thenReturn(
                new HomepageStatsResponse(0L, 0L, 0L, 0L)
        );

        mockMvc.perform(get("/api/v1/public/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
