package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItem;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLink;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLinkRepository;
import com.envisionad.webservice.utils.EmailService;
import com.envisionad.webservice.utils.EmailTexts;
import com.envisionad.webservice.utils.MediaOwnerNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The monthly reminders' content and batching (P12 FR-4), against a real {@link MediaOwnerNotifier}
 * over mocks, so the assertions are on what actually reaches {@link EmailService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProofOfDisplayReminderServiceUnitTest {

    private static final String BASE_URL = "https://envision-ad.ca";

    @Mock private BundleSubscriptionRepository subscriptionRepository;
    @Mock private BundleSubscriptionItemRepository itemRepository;
    @Mock private ProofOfDisplayLinkRepository linkRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private BundleRepository bundleRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private Auth0Service auth0Service;
    @Mock private EmailService emailService;

    private ProofOfDisplayReminderService service;

    private final List<BundleSubscription> live = new ArrayList<>();
    private final List<BundleSubscriptionItem> items = new ArrayList<>();
    private final List<ProofOfDisplayLink> links = new ArrayList<>();
    private final List<Business> businesses = new ArrayList<>();
    private final List<Employee> employees = new ArrayList<>();
    private final Map<String, Auth0Service.UserContact> contactsByUserId = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ProofOfDisplayReminderService(subscriptionRepository, itemRepository,
                new ProofOfDisplayEligibilityService(itemRepository), linkRepository, businessRepository,
                bundleRepository, new MediaOwnerNotifier(employeeRepository, auth0Service, emailService), new EmailTexts(),
                BASE_URL);

        when(subscriptionRepository.findAllByStatusIn(BundleSubscriptionStatus.LIVE)).thenReturn(live);
        when(itemRepository.findAllBySubscriptionIdIn(anyCollection())).thenReturn(items);
        when(linkRepository.findBySubscriptionIdIn(anyCollection())).thenReturn(links);
        when(businessRepository.findAllByBusinessId_BusinessIdIn(anyList())).thenReturn(businesses);
        when(employeeRepository.findAllByBusinessId_BusinessIdInOrderById(anyCollection())).thenReturn(employees);
        when(auth0Service.findContactsByUserIds(anyList())).thenReturn(contactsByUserId);
        Bundle bundle = new Bundle();
        bundle.setNameEn("Brossard Bundle");
        bundle.setNameFr("Forfait Brossard");
        when(bundleRepository.findByBundleId(anyString())).thenReturn(Optional.of(bundle));
    }

    // ---------- media owners ----------

    @Test
    void ownerReminder_isOneEmailPerOwnerListingEverySubscriptionAndItsLinkStatus() {
        givenBusiness("owner-a", "Screens Inc", "owner@screens.test");
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenBusiness("adv-2", "Acme", "acme@acme.test");
        String withLink = givenLiveSubscription("adv-1", "owner-a", "owner-a");
        givenLiveSubscription("adv-2", "owner-a");
        givenLink(withLink, "https://www.canva.com/lotus");

        MediaOwnerNotifier.Outcome outcome = service.sendMediaOwnerReminders();

        assertEquals(1, outcome.notified());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("owner@screens.test"),
                eq("Monthly check: your proof-of-display links"), body.capture());
        assertTrue(body.getValue().contains("- Acme, Brossard Bundle: no link yet, please add one"));
        assertTrue(body.getValue().contains("- Lotus Yoga, Brossard Bundle: link set (https://www.canva.com/lotus)"));
        assertTrue(body.getValue().indexOf("Acme") < body.getValue().indexOf("Lotus Yoga"), "sorted by advertiser");
        assertTrue(body.getValue().contains(BASE_URL + "/dashboard/media-owner/proof"));
    }

    @Test
    void ownerReminder_skipsMultiOwnerSubscriptions() {
        givenBusiness("owner-a", "Screens Inc", "a@screens.test");
        givenBusiness("owner-b", "Other Screens", "b@screens.test");
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenLiveSubscription("adv-1", "owner-a", "owner-b");

        assertEquals(0, service.sendMediaOwnerReminders().notified());
        verifyNoInteractions(emailService);
    }

    @Test
    void ownerReminder_oneUnresolvableOwnerDoesNotStopTheOthers() {
        givenBusiness("owner-a", "Screens Inc", null);
        givenBusiness("owner-b", "Other Screens", "b@screens.test");
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenLiveSubscription("adv-1", "owner-a");
        givenLiveSubscription("adv-1", "owner-b");

        MediaOwnerNotifier.Outcome outcome = service.sendMediaOwnerReminders();

        assertEquals(1, outcome.notified());
        assertEquals(1, outcome.failed());
        verify(emailService).sendSimpleEmail(eq("b@screens.test"), anyString(), anyString());
    }

    @Test
    void ownerReminder_withNothingLive_sendsNothingAndLoadsNothingElse() {
        assertEquals(new MediaOwnerNotifier.Outcome(0, 0), service.sendMediaOwnerReminders());
        verifyNoInteractions(itemRepository, emailService);
    }

    // ---------- advertisers ----------

    @Test
    void advertiserReminder_isOneEmailPerAdvertiser_listingEachSubscriptionsProofLink() {
        givenBusiness("owner-a", "Screens Inc", "owner@screens.test");
        givenBusiness("owner-b", "Panneaux Rive-Sud", "rivesud@screens.test");
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        String withLink = givenLiveSubscription("adv-1", "owner-a");
        givenLiveSubscription("adv-1", "owner-b");
        givenLink(withLink, "https://www.canva.com/lotus");

        MediaOwnerNotifier.Outcome outcome = service.sendAdvertiserReminders();

        assertEquals(1, outcome.notified());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendSimpleEmail(eq("lotus@yoga.test"),
                eq("Your monthly proof of display is ready to check"), body.capture());
        String text = body.getValue();
        // The proof opens straight from the email, no login needed.
        assertTrue(text.contains("- Brossard Bundle (Screens Inc): https://www.canva.com/lotus\n"), text);
        assertTrue(text.contains("- Brossard Bundle (Panneaux Rive-Sud): no link yet, "
                + "your media owner has been reminded to add one\n"), text);
        assertTrue(text.indexOf("(Panneaux Rive-Sud)") < text.indexOf("(Screens Inc)"), "sorted: " + text);
        assertTrue(text.contains(BASE_URL + "/dashboard/advertiser/subscriptions"), text);
        verify(emailService, never()).sendSimpleEmail(eq("owner@screens.test"), anyString(), anyString());
        verify(emailService, never()).sendSimpleEmail(eq("rivesud@screens.test"), anyString(), anyString());
    }

    @Test
    void advertiserReminder_fallsBackWhenTheOwnerOrBundleIsGone() {
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenLiveSubscription("adv-1", "deleted-owner");
        when(bundleRepository.findByBundleId(anyString())).thenReturn(Optional.empty());

        service.sendAdvertiserReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("lotus@yoga.test"), anyString(), body.capture());
        assertTrue(body.getValue().contains("- a bundle (your media owner): no link yet"), body.getValue());
        assertFalse(body.getValue().contains("null"));
    }

    @Test
    void advertiserReminder_skipsAnAdvertiserWhoseOnlySubscriptionIsMultiOwner() {
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenBusiness("adv-2", "Acme", "acme@acme.test");
        givenLiveSubscription("adv-1", "owner-a", "owner-b");
        givenLiveSubscription("adv-2", "owner-a");

        service.sendAdvertiserReminders();

        verify(emailService).sendSimpleEmail(eq("acme@acme.test"), anyString(), anyString());
        verify(emailService, never()).sendSimpleEmail(eq("lotus@yoga.test"), anyString(), anyString());
    }

    @Test
    void advertiserReminder_withNothingLive_sendsNothing() {
        assertEquals(new MediaOwnerNotifier.Outcome(0, 0), service.sendAdvertiserReminders());
        verifyNoInteractions(emailService);
    }

    @Test
    void ownerReminder_fallsBackWhenTheAdvertiserOrBundleIsGone() {
        givenBusiness("owner-a", "Screens Inc", "owner@screens.test");
        givenLiveSubscription("deleted-advertiser", "owner-a");
        when(bundleRepository.findByBundleId(anyString())).thenReturn(Optional.empty());

        service.sendMediaOwnerReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("owner@screens.test"), anyString(), body.capture());
        assertTrue(body.getValue().contains("- An advertiser, a bundle: no link yet"));
        assertFalse(body.getValue().contains("null"));
    }

    // ---------- language ----------

    @Test
    void reminders_goOutInEachRecipientsLanguage_frenchByDefault() {
        givenBusiness("owner-a", "Écrans Inc", "owner@screens.test", null);
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test", null);
        String withLink = givenLiveSubscription("adv-1", "owner-a");
        givenLiveSubscription("adv-1", "owner-a");
        givenLink(withLink, "https://www.canva.com/lotus");

        service.sendMediaOwnerReminders();
        service.sendAdvertiserReminders();

        ArgumentCaptor<String> ownerBody = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("owner@screens.test"),
                eq("Vérification mensuelle : vos liens de preuve d'affichage"), ownerBody.capture());
        assertTrue(ownerBody.getValue().startsWith("Bonjour,\n\nC'est la fin du mois."), ownerBody.getValue());
        assertTrue(ownerBody.getValue().contains(
                "- Lotus Yoga, Forfait Brossard : lien ajouté (https://www.canva.com/lotus)\n"), ownerBody.getValue());
        assertTrue(ownerBody.getValue().contains(
                "- Lotus Yoga, Forfait Brossard : aucun lien pour l'instant, veuillez en ajouter un\n"),
                ownerBody.getValue());

        ArgumentCaptor<String> advertiserBody = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendSimpleEmail(eq("lotus@yoga.test"),
                eq("Votre preuve d'affichage mensuelle est prête à consulter"), advertiserBody.capture());
        assertTrue(advertiserBody.getValue().contains(
                "- Forfait Brossard (Écrans Inc) : https://www.canva.com/lotus\n"), advertiserBody.getValue());
        assertTrue(advertiserBody.getValue().contains("- Forfait Brossard (Écrans Inc) : aucun lien pour l'instant"),
                advertiserBody.getValue());
        assertTrue(advertiserBody.getValue().contains(BASE_URL + "/dashboard/advertiser/subscriptions"),
                advertiserBody.getValue());
    }

    @Test
    void englishReminders_areWordForWordThePreviousText() {
        givenBusiness("owner-a", "Screens Inc", "owner@screens.test");
        givenBusiness("adv-1", "Lotus Yoga", "lotus@yoga.test");
        givenLink(givenLiveSubscription("adv-1", "owner-a"), "https://www.canva.com/lotus");

        service.sendMediaOwnerReminders();
        service.sendAdvertiserReminders();

        verify(emailService).sendSimpleEmail("owner@screens.test", "Monthly check: your proof-of-display links",
                "Hi there,\n\n"
                        + "It's the end of the month. Please check that the proof-of-display page for each of "
                        + "these subscriptions shows your screens as they are now. The advertisers will be "
                        + "reminded to look at it tomorrow.\n\n"
                        + "- Lotus Yoga, Brossard Bundle: link set (https://www.canva.com/lotus)\n"
                        + "\nAdd or update your links here:\n"
                        + BASE_URL + "/dashboard/media-owner/proof\n\n"
                        + "Thanks,\n— The Envision Ad Team");
        verify(emailService).sendSimpleEmail("lotus@yoga.test", "Your monthly proof of display is ready to check",
                "Hi there,\n\n"
                        + "Your media owners keep a proof-of-display page for each of your subscriptions, "
                        + "showing your ad on their screens. Here is this month's:\n\n"
                        + "- Brossard Bundle (Screens Inc): https://www.canva.com/lotus\n"
                        + "\nYou can also see them all on your Subscriptions page:\n"
                        + BASE_URL + "/dashboard/advertiser/subscriptions\n\n"
                        + "Thanks,\n— The Envision Ad Team");
    }

    // ---------- fixtures ----------

    /**
     * A business whose first employee resolves to {@code email} (or to nothing when null), and who
     * picked English in the app, so the texts are the pre-i18n English.
     */
    private void givenBusiness(String businessId, String name, String email) {
        givenBusiness(businessId, name, email, "en");
    }

    private void givenBusiness(String businessId, String name, String email, String preferredLanguage) {
        Business business = new Business();
        business.setBusinessId(new BusinessIdentifier(businessId));
        business.setName(name);
        businesses.add(business);

        Employee employee = new Employee();
        employee.setBusinessId(new BusinessIdentifier(businessId));
        employee.setUserId("auth0|" + businessId);
        employees.add(employee);
        if (email != null) {
            contactsByUserId.put("auth0|" + businessId, new Auth0Service.UserContact(email, preferredLanguage));
        }
    }

    private String givenLiveSubscription(String advertiserBusinessId, String... ownerBusinessIds) {
        BundleSubscription subscription = new BundleSubscription();
        String subscriptionId = UUID.randomUUID().toString();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setBundleId("bundle-1");
        subscription.setAdvertiserBusinessId(advertiserBusinessId);
        subscription.setStatus(BundleSubscriptionStatus.ACTIVE);
        live.add(subscription);
        Arrays.stream(ownerBusinessIds).forEach(owner -> {
            BundleSubscriptionItem item = new BundleSubscriptionItem();
            item.setSubscriptionId(subscriptionId);
            item.setMediaId(UUID.randomUUID());
            item.setMediaOwnerBusinessId(owner);
            item.setMonthlyAmount(new BigDecimal("4.00"));
            items.add(item);
        });
        return subscriptionId;
    }

    private void givenLink(String subscriptionId, String url) {
        ProofOfDisplayLink link = new ProofOfDisplayLink();
        link.setSubscriptionId(subscriptionId);
        link.setUrl(url);
        links.add(link);
    }
}
