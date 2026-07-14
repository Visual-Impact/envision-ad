package com.envisionad.webservice.homepage.presentationlayer;

import com.envisionad.webservice.homepage.businesslogiclayer.HomepageStatsService;
import com.envisionad.webservice.homepage.presentationlayer.models.HomepageStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://localhost:3000", "https://envision-ad.ca" })
public class HomepageStatsController {

    private final HomepageStatsService service;

    @GetMapping("/stats")
    public HomepageStatsResponse getStats() {
        return service.getStats();
    }
}
