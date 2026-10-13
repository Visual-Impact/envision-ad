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
import java.util.Map;
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
    private final String appBaseUrl;

    public ProofOfDisplayReminderService(BundleSubscriptionRepository subscriptionRepository,
                                         BundleSubscriptionItemRepository itemRepository,
                                         ProofOfDisplayEligibilityService eligibilityService,
                                         ProofOfDisplayLinkRepository linkRepository,
                                         BusinessRepository businessRepository,
                                         BundleRepository bundleRepository,
                                         MediaOwnerNotifier notifier,
                                         @Value("${app.base.url}") String appBaseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.itemRepository = itemRepository;
        this.eligibilityService = eligibilityService;
        this.linkRepository = linkRepository;
        this.businessRepository = businessRepository;
        this.bundleRepository = bundleRepository;
        this.notifier = notifier;
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
        Map<String, String> bundleNames = new HashMap<>();

        Map<String, List<Eligible>> byOwner = eligible.stream()
                .collect(Collectors.groupingBy(Eligible::ownerBusinessId, LinkedHashMap::new, Collectors.toList()));

        List<OwnerMessage> messages = new ArrayList<>();
        byOwner.forEach((ownerBusinessId, subscriptions) -> {
            StringBuilder body = new StringBuilder("Hi there,\n\n")
                    .append("It's the end of the month. Please check that the proof-of-display page for each of ")
                    .append("these subscriptions shows your screens as they are now. The advertisers will be ")
                    .append("reminded to look at it tomorrow.\n\n");
            subscriptions.stream()
                    .sorted(Comparator.comparing(e -> advertiserNames.getOrDefault(
                            e.subscription().getAdvertiserBusinessId(), "")))
                    .forEach(e -> {
                        BundleSubscription subscription = e.subscription();
                        ProofOfDisplayLink link = links.get(subscription.getSubscriptionId());
                        body.append("- ")
                                .append(advertiserNames.getOrDefault(subscription.getAdvertiserBusinessId(),
                                        "An advertiser"))
                                .append(", ")
                                .append(bundleNames.computeIfAbsent(subscription.getBundleId(), this::bundleName))
                                .append(": ")
                                .append(link == null ? "no link yet, please add one" : "link set (" + link.getUrl() + ")")
                                .append("\n");
                    });
            body.append("\nAdd or update your links here:\n")
                    .append(appBaseUrl).append("/dashboard/media-owner/proof\n\n")
                    .append("Thanks,\n— The Envision Ad Team");
            messages.add(new OwnerMessage(ownerBusinessId, "Monthly check: your proof-of-display links",
                    body.toString()));
        });

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
        Map<String, String> bundleNames = new HashMap<>();

        Map<String, List<Eligible>> byAdvertiser = eligible.stream()
                .collect(Collectors.groupingBy(e -> e.subscription().getAdvertiserBusinessId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<OwnerMessage> messages = new ArrayList<>();
        byAdvertiser.forEach((advertiserBusinessId, subscriptions) -> {
            StringBuilder body = new StringBuilder("Hi there,\n\n")
                    .append("Your media owners keep a proof-of-display page for each of your subscriptions, ")
                    .append("showing your ad on their screens. Here is this month's:\n\n");
            subscriptions.stream()
                    .map(e -> {
                        BundleSubscription subscription = e.subscription();
                        ProofOfDisplayLink link = links.get(subscription.getSubscriptionId());
                        return "- " + bundleNames.computeIfAbsent(subscription.getBundleId(), this::bundleName)
                                + " (" + ownerNames.getOrDefault(e.ownerBusinessId(), "your media owner") + "): "
                                + (link == null ? "no link yet, your media owner has been reminded to add one"
                                        : link.getUrl());
                    })
                    .sorted()
                    .forEach(line -> body.append(line).append("\n"));
            body.append("\nYou can also see them all on your Subscriptions page:\n")
                    .append(appBaseUrl).append("/dashboard/advertiser/subscriptions\n\n")
                    .append("Thanks,\n— The Envision Ad Team");
            messages.add(new OwnerMessage(advertiserBusinessId, "Your monthly proof of display is ready to check",
                    body.toString()));
        });

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

    private String bundleName(String bundleId) {
        return bundleRepository.findByBundleId(bundleId).map(Bundle::getNameEn).orElse("a bundle");
    }
}
