package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.advertisement.dataaccesslayer.AdCampaignRepository;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.media.DataAccessLayer.MediaRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLink;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLinkRepository;
import com.envisionad.webservice.proofofdisplay.exceptions.InvalidProofLinkUrlException;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofLinkResponseModel;
import com.envisionad.webservice.proofofdisplay.presentationlayer.models.ProofLinkStatus;
import com.envisionad.webservice.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * URL validation (the XSS control, FR-1.2 / D8) and the paths the integration test can't reach
 * cheaply. The endpoint behaviour itself is covered against Postgres in
 * {@code ProofOfDisplayControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProofOfDisplayLinkServiceUnitTest {

    @Mock private ProofOfDisplayLinkRepository linkRepository;
    @Mock private BundleSubscriptionRepository subscriptionRepository;
    @Mock private BundleSubscriptionItemRepository itemRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private BundleRepository bundleRepository;
    @Mock private AdCampaignRepository adCampaignRepository;
    @Mock private MediaRepository mediaRepository;

    private ProofOfDisplayLinkService service;

    @BeforeEach
    void setUp() {
        service = new ProofOfDisplayLinkService(linkRepository,
                new ProofOfDisplayEligibilityService(itemRepository), subscriptionRepository, itemRepository,
                businessRepository, employeeRepository, bundleRepository, adCampaignRepository, mediaRepository,
                new JwtUtils(employeeRepository));
    }

    // ---------- validateUrl ----------

    @Test
    void validateUrl_acceptsHttpsAndHttp_andTrimsSurroundingWhitespace() {
        assertEquals("https://www.canva.com/design/abc/view",
                ProofOfDisplayLinkService.validateUrl("  https://www.canva.com/design/abc/view \n"));
        assertEquals("http://proof.example.com/page", ProofOfDisplayLinkService.validateUrl("http://proof.example.com/page"));
        assertEquals("HTTPS://Example.com", ProofOfDisplayLinkService.validateUrl("HTTPS://Example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://example.com/file",
            "www.canva.com/design/abc",
            "https://",
            "https:///path-only",
            "http://exa mple.com",
            "",
            "   "
    })
    void validateUrl_rejects(String url) {
        assertThrows(InvalidProofLinkUrlException.class, () -> ProofOfDisplayLinkService.validateUrl(url));
    }

    @Test
    void validateUrl_rejectsNull() {
        assertThrows(InvalidProofLinkUrlException.class, () -> ProofOfDisplayLinkService.validateUrl(null));
    }

    @Test
    void validateUrl_rejectsAnythingLongerThanTheCap() {
        String base = "https://www.canva.com/";
        String atCap = base + "a".repeat(ProofOfDisplayLinkService.MAX_URL_LENGTH - base.length());
        assertEquals(atCap, ProofOfDisplayLinkService.validateUrl(atCap));
        assertThrows(InvalidProofLinkUrlException.class,
                () -> ProofOfDisplayLinkService.validateUrl(atCap + "a"));
    }

    // ---------- upsertLink ----------

    @Test
    void upsertLink_byAUserWithNoBusiness_isDeniedBeforeAnyOwnershipLookup() {
        BundleSubscription subscription = new BundleSubscription();
        subscription.setSubscriptionId("sub-1");
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findBySubscriptionId("sub-1")).thenReturn(Optional.of(subscription));
        when(employeeRepository.findByUserId("auth0|admin")).thenReturn(null);

        Jwt admin = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "auth0|admin").build();

        assertThrows(AccessDeniedException.class,
                () -> service.upsertLink(admin, "sub-1", "https://www.canva.com/x"));
        verifyNoInteractions(itemRepository);
        verify(linkRepository, never()).save(any());
    }

    // ---------- getProofLinks ----------

    @Test
    void getProofLinks_forNoSubscriptions_queriesNothing() {
        assertTrue(service.getProofLinks(List.of()).isEmpty());
        verifyNoInteractions(itemRepository, linkRepository);
    }

    @Test
    void getProofLinks_resolvesEachOfTheThreeStates() {
        when(itemRepository.findAllBySubscriptionIdIn(List.of("set", "unset", "shared"))).thenReturn(List.of(
                item("set", "owner-a"),
                item("unset", "owner-a"), item("unset", "owner-a"),
                item("shared", "owner-a"), item("shared", "owner-b")));
        when(linkRepository.findBySubscriptionIdIn(List.of("set", "unset", "shared"))).thenReturn(List.of(
                link("set", "https://www.canva.com/set"),
                // The PUT refuses multi-owner subscriptions, so this can only be stale data: not shown.
                link("shared", "https://www.canva.com/stale")));

        Map<String, ProofLinkResponseModel> links = service.getProofLinks(List.of("set", "unset", "shared"));

        assertEquals(ProofLinkStatus.SET, links.get("set").getStatus());
        assertEquals("https://www.canva.com/set", links.get("set").getUrl());
        assertEquals(ProofLinkStatus.NOT_SET, links.get("unset").getStatus());
        assertNull(links.get("unset").getUrl());
        assertEquals(ProofLinkStatus.NOT_APPLICABLE, links.get("shared").getStatus());
        assertNull(links.get("shared").getUrl());
    }

    @Test
    void getProofLinks_aSubscriptionWithNoItemsIsNotApplicable() {
        when(itemRepository.findAllBySubscriptionIdIn(List.of("empty"))).thenReturn(List.of());
        when(linkRepository.findBySubscriptionIdIn(List.of("empty"))).thenReturn(List.of());

        assertEquals(ProofLinkStatus.NOT_APPLICABLE, service.getProofLinks(List.of("empty")).get("empty").getStatus());
    }

    private static BundleSubscriptionItem item(String subscriptionId, String owner) {
        BundleSubscriptionItem item = new BundleSubscriptionItem();
        item.setSubscriptionId(subscriptionId);
        item.setMediaId(UUID.randomUUID());
        item.setMediaOwnerBusinessId(owner);
        item.setMonthlyAmount(new BigDecimal("4.00"));
        return item;
    }

    private static ProofOfDisplayLink link(String subscriptionId, String url) {
        ProofOfDisplayLink link = new ProofOfDisplayLink();
        link.setSubscriptionId(subscriptionId);
        link.setUrl(url);
        return link;
    }
}
