package com.envisionad.webservice.media.PresentationLayer;

import com.envisionad.webservice.business.dataaccesslayer.*;
import com.envisionad.webservice.config.BaseIntegrationTest;
import com.envisionad.webservice.media.DataAccessLayer.Media;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocation;
import com.envisionad.webservice.media.DataAccessLayer.MediaLocationRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.media.DataAccessLayer.Status;
import com.envisionad.webservice.media.DataAccessLayer.TypeOfDisplay;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.MediaStatusPatchRequestModel;
import com.envisionad.webservice.media.PresentationLayer.Models.ScheduleModel;
import com.envisionad.webservice.media.PresentationLayer.Models.WeeklyScheduleEntry;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.envisionad.webservice.payment.dataaccesslayer.StripeAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.BodyInserters;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class MediaControllerIntegrationTest extends BaseIntegrationTest {

        private final String BASE_URI_MEDIA = "/api/v1/media";
        private final String BUSINESS_ID = UUID.randomUUID().toString();

        @Autowired
        private BusinessRepository businessRepository;

        @Autowired
        private EmployeeRepository employeeRepository;

        @Autowired
        private StripeAccountRepository stripeAccountRepository;

        @Autowired
        private MediaRepository mediaRepository;

        @Autowired
        private MediaLocationRepository mediaLocationRepository;

        private String mediaId;
        private String mediaLocationId;

        @BeforeEach
        void setUp() {
                mediaRepository.deleteAll();
                mediaLocationRepository.deleteAll();
                stripeAccountRepository.deleteAll();
                employeeRepository.deleteAll();
                businessRepository.deleteAll();

                Jwt jwt = Jwt.withTokenValue("mock-token")
                                .header("alg", "none")
                                .claim("sub", "auth0|65702e81e9661e14ab3aac89")
                                .claim("scope", "read write")
                                .claim("permissions", List.of(
                                                "create:employee",
                                                "create:media",
                                                "delete:employee",
                                                "get:media",
                                                "read:employee",
                                                "update:business",
                                                "update:media"))
                                .build();
                when(jwtDecoder.decode("mock-token")).thenReturn(jwt);

                // Create Business (required by BusinessService.getBusinessByUserId)
                Business business = new Business();
                business.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
                business.setName("Test Business");
                business.setOwnerId("auth0|65702e81e9661e14ab3aac89");
                business.setOrganizationSize(OrganizationSize.SMALL);
                business.setVerified(true);
                Address address = new Address();
                address.setStreet("123 Test St");
                address.setCity("Toronto");
                address.setState("ON");
                address.setZipCode("M5H 1A1");
                address.setCountry("Canada");
                business.setAddress(address);
                Roles roles = new Roles();
                roles.setMediaOwner(true);
                business.setRoles(roles);
                businessRepository.save(business);

                // Create Employee (required by BusinessService.getBusinessByUserId and media auth checks)
                Employee employee = new Employee();
                employee.setEmployeeId(new EmployeeIdentifier());
                employee.setUserId("auth0|65702e81e9661e14ab3aac89");
                employee.setBusinessId(new BusinessIdentifier(BUSINESS_ID));
                employeeRepository.save(employee);

                // Create StripeAccount (required to pass onboarding check when adding media)
                StripeAccount stripeAccount = new StripeAccount();
                stripeAccount.setBusinessId(BUSINESS_ID);
                stripeAccount.setStripeAccountId("acct_test_" + BUSINESS_ID.replace("-", "").substring(0, 16));
                stripeAccount.setOnboardingComplete(true);
                stripeAccount.setChargesEnabled(true);
                stripeAccount.setPayoutsEnabled(true);
                stripeAccountRepository.save(stripeAccount);

                MediaLocation location = new MediaLocation();
                location.setName("Downtown Billboard A");
                location.setBusinessId(UUID.fromString(BUSINESS_ID));
                location.setCountry("Canada");
                location.setProvince("ON");
                location.setCity("Toronto");
                location.setStreet("123 King St W");
                location.setPostalCode("M5H 1A1");
                location.setLatitude(43.651070);
                location.setLongitude(-79.347015);
                mediaLocationRepository.save(location);
                this.mediaLocationId = location.getId().toString();

                Media media = new Media();
                media.setMediaLocation(location);
                media.setTitle("Downtown Digital Board");
                media.setMediaOwnerName("MetroAds");
                media.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                media.setLoopDuration(30);
                media.setResolution("1920x1080");

                media.setWidth(1920.0);
                media.setHeight(1080.0);
                media.setPrice(new BigDecimal("150.00"));
                media.setDailyImpressions(25000);
                media.setStatus(Status.ACTIVE);
                media.setBusinessId(UUID.fromString(BUSINESS_ID));

                ScheduleModel schedule = new ScheduleModel();
                WeeklyScheduleEntry entry = new WeeklyScheduleEntry();
                entry.setDayOfWeek("Monday");
                entry.setActive(true);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
                schedule.setWeeklySchedule(List.of(entry));
                media.setSchedule(schedule);

                media.setImageUrl("http://example.com/image.jpg");
                media.setPreviewConfiguration("{\"corners\": []}");
                mediaRepository.save(media);
                this.mediaId = media.getId().toString();
        }

        @Test
        void addMedia_ShouldPersistAndReturnMedia() {
                // Arrange
                MediaRequestModel requestModel = new MediaRequestModel();
                requestModel.setTitle("Integration Test Media");
                requestModel.setMediaOwnerName("Integration Owner");
                requestModel.setMediaLocationId(this.mediaLocationId);
                requestModel.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                requestModel.setPrice(new BigDecimal("200.00"));
                requestModel.setStatus(Status.ACTIVE);
                requestModel.setDailyImpressions(1000);
                requestModel.setWidth(1920.0);
                requestModel.setHeight(1080.0);
                requestModel.setResolution("1920x1080");
                requestModel.setLoopDuration(15);

                ScheduleModel schedule = new ScheduleModel();
                WeeklyScheduleEntry entry = new WeeklyScheduleEntry();
                entry.setDayOfWeek("Monday");
                entry.setActive(true);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
                schedule.setWeeklySchedule(List.of(entry));
                requestModel.setSchedule(schedule);
                requestModel.setImageUrl("http://example.com/new_image.jpg");
                requestModel.setPreviewConfiguration("{\"corners\": []}");

                // Act & Assert
                webTestClient.post()
                                .uri(BASE_URI_MEDIA)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth("mock-token"))
                                .body(BodyInserters.fromValue(requestModel))
                                .exchange()
                                .expectStatus().isCreated()
                                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                                .expectBody()
                                .jsonPath("$.title").isEqualTo("Integration Test Media")
                                .jsonPath("$.id").isNotEmpty();

                assertEquals(2, mediaRepository.count());
        }

        @Test
        void getAllMedia_ShouldReturnAllMedia() {
                // Act & Assert
                webTestClient.get()
                                .uri(BASE_URI_MEDIA)
                                .accept(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth("mock-token"))
                                .exchange()
                                .expectStatus().isOk()
                                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                                .expectBody()
                                .jsonPath("$.length()").isEqualTo(1);
        }

        @Test
        void getAllMedia_WithoutAuthentication_ShouldReturnForbidden() {
                // Act & Assert
                webTestClient.get()
                                .uri(BASE_URI_MEDIA)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isForbidden();
        }

        @Test
        void getMediaById_ShouldReturnOneMedia() {
                // Act & Assert
                webTestClient.get()
                                .uri(BASE_URI_MEDIA + "/{id}", this.mediaId)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isOk()
                                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                                .expectBody()
                                .jsonPath("$.id").isEqualTo(mediaId)
                                .jsonPath("$.title").isEqualTo("Downtown Digital Board");
        }

        @Test
        void updateMedia_ShouldUpdateAndReturnMedia() {
                MediaRequestModel updateRequest = new MediaRequestModel();
                updateRequest.setTitle("Updated Title");
                updateRequest.setMediaOwnerName("Integration Owner");
                updateRequest.setMediaLocationId(this.mediaLocationId);
                updateRequest.setTypeOfDisplay(TypeOfDisplay.DIGITAL);
                updateRequest.setStatus(Status.ACTIVE);

                ScheduleModel schedule = new ScheduleModel();
                WeeklyScheduleEntry entry = new WeeklyScheduleEntry();
                entry.setDayOfWeek("Monday");
                entry.setActive(true);
                entry.setStartTime("09:00");
                entry.setEndTime("17:00");
                schedule.setWeeklySchedule(List.of(entry));
                updateRequest.setPrice(new BigDecimal("300.00"));
                updateRequest.setDailyImpressions(2000);
                updateRequest.setResolution("1920x1080");
                updateRequest.setLoopDuration(20);
                updateRequest.setSchedule(schedule);
                updateRequest.setImageUrl("http://example.com/updated_image.jpg");
                updateRequest.setPreviewConfiguration("{\"corners\": []}");

                // Act & Assert
                webTestClient.put()
                                .uri(BASE_URI_MEDIA + "/{id}", this.mediaId)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth("mock-token"))
                                .body(BodyInserters.fromValue(updateRequest))
                                .exchange()
                                .expectStatus().isOk()
                                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                                .expectBody()
                                .jsonPath("$.title").isEqualTo("Updated Title");

                Media updatedMedia = mediaRepository.findById(UUID.fromString(this.mediaId)).orElseThrow();
                assertEquals("Updated Title", updatedMedia.getTitle());
                assertEquals(UUID.fromString(BUSINESS_ID), updatedMedia.getBusinessId());
        }

        @Test
        void deleteMedia_ShouldRemoveMedia() {
                // Arrange
                Media media = mediaRepository.findAll().getFirst();
                String mediaIdToDelete = media.getId().toString();

                // Act & Assert
                webTestClient.delete()
                                .uri(BASE_URI_MEDIA + "/{id}", mediaIdToDelete)
                                .header("Authorization", "Bearer mock-token")
                                .exchange()
                                .expectStatus().isNoContent();

                assertEquals(0, mediaRepository.count());
        }

        @Test
        void patchMediaStatus_WhenInvalidTransition_ShouldReturnConflict() {
                MediaStatusPatchRequestModel request = new MediaStatusPatchRequestModel();
                request.setStatus(Status.PENDING); // seeded media is ACTIVE; ACTIVE->PENDING is disallowed

                webTestClient.patch()
                                .uri(BASE_URI_MEDIA + "/{id}/status", this.mediaId)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth("mock-token"))
                                .body(BodyInserters.fromValue(request))
                                .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                                .expectBody()
                                .jsonPath("$.message").isEqualTo("Invalid status transition: ACTIVE -> PENDING");
        }

        @Test
        void patchMediaStatus_WhenRejectedReactivated_ShouldReturnConflict() {
                Media media = mediaRepository.findById(UUID.fromString(this.mediaId)).orElseThrow();
                media.setStatus(Status.REJECTED);
                mediaRepository.save(media);

                MediaStatusPatchRequestModel request = new MediaStatusPatchRequestModel();
                request.setStatus(Status.ACTIVE);

                webTestClient.patch()
                                .uri(BASE_URI_MEDIA + "/{id}/status", this.mediaId)
                                .accept(MediaType.APPLICATION_JSON)
                                .contentType(MediaType.APPLICATION_JSON)
                                .headers(headers -> headers.setBearerAuth("mock-token"))
                                .body(BodyInserters.fromValue(request))
                                .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                                .expectBody()
                                .jsonPath("$.message").isEqualTo("Rejected media cannot be re-activated. Please create a new media item instead.");
        }
}
