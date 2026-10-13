package com.envisionad.webservice.proofofdisplay.businesslogiclayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.dataaccesslayer.BundleRepository;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscription;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLink;
import com.envisionad.webservice.proofofdisplay.dataaccesslayer.ProofOfDisplayLinkRepository;
import com.envisionad.webservice.utils.EmailContent;
import com.envisionad.webservice.utils.EmailTexts;
import com.envisionad.webservice.utils.MediaOwnerNotifier;
import com.envisionad.webservice.utils.MediaOwnerNotifier.OwnerMessage;
import com.envisionad.webservice.utils.MediaOwnerNotifier.Outcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The two monthly proof-of-display reminders (P12 FR-4): media owners on the last day of the month
 * (check your links are current), advertisers on the 1st (go and look). The platform never checks
 * the linked page itself; it only makes sure both sides are nudged.
 *
 * <p>Plain methods, called by {@code ProofOfDisplayScheduledJobs}, so they are tested directly
 * rather than by waiting for a calendar date.
 *
 * <p>Both go through {@link MediaOwnerNotifier}, which despite its name resolves "first employee
 * of a business → Auth0 email" for any business and isolates per-business failures (FR-6.5: one
 * unresolvable business must not stop the batch). Advertisers are businesses like any other there.
 */
@Slf4j
@Service
public class ProofOfDisplayReminderService {

    private final BundleSubscriptionRepository subscriptionRepository;
    private final BundleSubscriptionItemRepository itemRepository;
    private final ProofOfDisplayEligibilityService eligibilityService;
    private final ProofOfDisplayLinkRepository linkRepository;
    private final BusinessRepository businessRepository;
    private final BundleRepository bundleRepository;
    private final MediaOwnerNotifier notifier;
    private final EmailTexts emailTexts;
    private final String appBaseUrl;

    public ProofOfDisplayReminderService(BundleSubscriptionRepository subscriptionRepository,
                                         BundleSubscriptionItemRepository itemRepository,
                                         ProofOfDisplayEligibilityService eligibilityService,
                                         ProofOfDisplayLinkRepository linkRepository,
                                         BusinessRepository businessRepository,
                                         BundleRepository bundleRepository,
                                         MediaOwnerNotifier notifier,
                                         EmailTexts emailTexts,
                                         @Value("${app.base.url}") String appBaseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.itemRepository = itemRepository;
        this.eligibilityService = eligibilityService;
        this.linkRepository = linkRepository;
        this.businessRepository = businessRepository;
        this.bundleRepository = bundleRepository;
        this.notifier = notifier;
        this.emailTexts = emailTexts;
        this.appBaseUrl = appBaseUrl;
    }

    /** A running, single-owner subscription: the only kind that can carry a link (FR-4.3). */
    private record Eligible(BundleSubscription subscription, String ownerBusinessId) {}

    /**
     * FR-4.1: one email per media-owner business, listing each of its eligible subscriptions and
     * whether the link is set or missing.
     */
    @Transactional(readOnly = true)
    public Outcome sendMediaOwnerReminders() {
        List<Eligible> eligible = findEligibleSubscriptions();
        if (eligible.isEmpty()) {
            return new Outcome(0, 0);
        }

        Map<String, ProofOfDisplayLink> links = linksBySubscription(eligible);
        Map<String, String> advertiserNames = businessNames(
                eligible.stream().map(e -> e.subscription().getAdvertiserBusinessId()).distinct().toList());
        Map<String, Optional<Bundle>> bundles = new HashMap<>();

        Map<String, List<Eligible>> byOwner = eligible.stream()
                .collect(Collectors.groupingBy(Eligible::ownerBusinessId, LinkedHashMap::new, Collectors.toList()));

        List<OwnerMessage> messages = new ArrayList<>();
        byOwner.forEach((ownerBusinessId, subscriptions) -> messages.add(new OwnerMessage(ownerBusinessId, locale -> {
            StringBuilder lines = new StringBuilder();
            subscriptions.stream()
                    .sorted(Comparator.comparing(e -> advertiserNames.getOrDefault(
                            e.subscription().getAdvertiserBusinessId(), "")))
                    .forEach(e -> {
                        BundleSubscription subscription = e.subscription();
                        ProofOfDisplayLink link = links.get(subscription.getSubscriptionId());
                        lines.append("- ")
                                .append(emailTexts.get("reminder.owner.line", locale,
                                        advertiserNames.getOrDefault(subscription.getAdvertiserBusinessId(),
                                                emailTexts.get("reminder.unknownAdvertiser", locale)),
                                        bundleName(bundles, subscription.getBundleId(), locale),
                                        link == null ? emailTexts.get("reminder.owner.linkMissing", locale)
                                                : emailTexts.get("reminder.owner.linkSet", locale, link.getUrl())))
                                .append("\n");
                    });
            return new EmailContent(
                    emailTexts.get("reminder.owner.subject", locale),
                    emailTexts.get("reminder.owner.body", locale,
                            lines.toString(), appBaseUrl + "/dashboard/media-owner/proof"));
        })));

        Outcome outcome = notifier.send(messages);
        log.info("Proof-of-display media-owner reminders: {} sent, {} failed", outcome.notified(), outcome.failed());
        return outcome;
    }

    /**
     * FR-4.2: one email per advertiser business with at least one eligible subscription, listing
     * each of them with its proof-of-display link, so the advertiser can open the proof straight
     * from the email without logging in. A subscription whose owner hasn't added a link yet is
     * listed as such (the owner was reminded the day before); the Subscriptions page link stays
     * at the bottom as the place to see them all.
     */
    @Transactional(readOnly = true)
    public Outcome sendAdvertiserReminders() {
        List<Eligible> eligible = findEligibleSubscriptions();
        if (eligible.isEmpty()) {
            return new Outcome(0, 0);
        }

        Map<String, ProofOfDisplayLink> links = linksBySubscription(eligible);
        Map<String, String> ownerNames = businessNames(
                eligible.stream().map(Eligible::ownerBusinessId).distinct().toList());
        Map<String, Optional<Bundle>> bundles = new HashMap<>();

        Map<String, List<Eligible>> byAdvertiser = eligible.stream()
                .collect(Collectors.groupingBy(e -> e.subscription().getAdvertiserBusinessId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<OwnerMessage> messages = new ArrayList<>();
        byAdvertiser.forEach((advertiserBusinessId, subscriptions) -> messages.add(new OwnerMessage(advertiserBusinessId,
                locale -> {
                    StringBuilder lines = new StringBuilder();
                    subscriptions.stream()
                            .map(e -> {
                                ProofOfDisplayLink link = links.get(e.subscription().getSubscriptionId());
                                return "- " + emailTexts.get("reminder.advertiser.line", locale,
                                        bundleName(bundles, e.subscription().getBundleId(), locale),
                                        ownerNames.getOrDefault(e.ownerBusinessId(),
                                                emailTexts.get("reminder.unknownOwner", locale)),
                                        link == null ? emailTexts.get("reminder.advertiser.linkMissing", locale)
                                                : link.getUrl());
                            })
                            .sorted()
                            .forEach(line -> lines.append(line).append("\n"));
                    return new EmailContent(
                            emailTexts.get("reminder.advertiser.subject", locale),
                            emailTexts.get("reminder.advertiser.body", locale,
                                    lines.toString(), appBaseUrl + "/dashboard/advertiser/subscriptions"));
                })));

        Outcome outcome = notifier.send(messages);
        log.info("Proof-of-display advertiser reminders: {} sent, {} failed", outcome.notified(), outcome.failed());
        return outcome;
    }

    private Map<String, ProofOfDisplayLink> linksBySubscription(List<Eligible> eligible) {
        return linkRepository.findBySubscriptionIdIn(
                        eligible.stream().map(e -> e.subscription().getSubscriptionId()).toList())
                .stream()
                .collect(Collectors.toMap(ProofOfDisplayLink::getSubscriptionId, Function.identity()));
    }

    /** ACTIVE or PAST_DUE, and every screen belongs to one media-owner business. */
    private List<Eligible> findEligibleSubscriptions() {
        List<BundleSubscription> live = subscriptionRepository.findAllByStatusIn(BundleSubscriptionStatus.LIVE);
        if (live.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> owners = eligibilityService.ownerBusinessIdsBySubscription(
                itemRepository.findAllBySubscriptionIdIn(live.stream().map(BundleSubscription::getSubscriptionId).toList()));

        List<Eligible> eligible = new ArrayList<>();
        for (BundleSubscription subscription : live) {
            Set<String> subscriptionOwners = owners.get(subscription.getSubscriptionId());
            if (ProofOfDisplayEligibilityService.isSingleOwner(subscriptionOwners)) {
                eligible.add(new Eligible(subscription, subscriptionOwners.iterator().next()));
            }
        }
        return eligible;
    }

    private Map<String, String> businessNames(List<String> businessIds) {
        return businessRepository.findAllByBusinessId_BusinessIdIn(businessIds).stream()
                .filter(b -> b.getName() != null)
                .collect(Collectors.toMap(b -> b.getBusinessId().getBusinessId(), Business::getName, (a, b) -> a));
    }

    /** The bundle's name in {@code locale}, looking each bundle up once per run. */
    private String bundleName(Map<String, Optional<Bundle>> bundles, String bundleId, Locale locale) {
        return bundles.computeIfAbsent(bundleId, bundleRepository::findByBundleId)
                .map(bundle -> Locale.ENGLISH.equals(locale) ? bundle.getNameEn() : bundle.getNameFr())
                .orElseGet(() -> emailTexts.get("reminder.unknownBundle", locale));
    }
}
