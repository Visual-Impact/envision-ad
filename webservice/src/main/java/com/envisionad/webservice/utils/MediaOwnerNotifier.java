package com.envisionad.webservice.utils;

import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.config.Auth0Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends one plain-text email to each of a set of media-owner businesses, isolating failures
 * so that one unreachable owner never costs the others their notification.
 *
 * <p>This exists because three call sites had grown the same loop independently — resolve one
 * employee per owner business, resolve that employee's email through Auth0, send, catch and log
 * per owner. P6 M2a would have been the fourth. Two things were wrong with the copies:
 *
 * <ul>
 *   <li>They resolved emails one Auth0 call at a time. That is the exact pattern that hit
 *       Auth0's rate limit during P5 M6 live testing and prompted
 *       {@link Auth0Service#findEmailsByUserIds(List)}; a campaign swap fans out to just as
 *       many owners as the admin Accounts page did. This class uses the batched call.</li>
 *   <li>Batching moves the Auth0 request <em>outside</em> the per-owner try/catch, which would
 *       let an Auth0 outage propagate into a caller's transaction — something the existing
 *       Stripe-webhook caller explicitly must not allow. Resolution is therefore guarded here
 *       and degrades to "no addresses resolved", not to a thrown exception.</li>
 * </ul>
 */
@Slf4j
@Component
public class MediaOwnerNotifier {

    private final EmployeeRepository employeeRepository;
    private final Auth0Service auth0Service;
    private final EmailService emailService;

    public MediaOwnerNotifier(EmployeeRepository employeeRepository,
                              Auth0Service auth0Service,
                              EmailService emailService) {
        this.employeeRepository = employeeRepository;
        this.auth0Service = auth0Service;
        this.emailService = emailService;
    }

    /** One owner business's message. Bodies are built by the caller; delivery is built here. */
    public record OwnerMessage(String ownerBusinessId, String subject, String body) {}

    /**
     * What actually happened, for the caller to record. P6 writes these straight into
     * {@code campaign_swap_events.recipients_notified} / {@code recipients_failed}, which is why
     * "no address could be resolved" counts as a failure rather than being silently dropped: an
     * owner who was supposed to hear about a swap and did not is a failure, whatever the cause.
     */
    public record Outcome(int notified, int failed) {}

    /**
     * Sends every message, one per owner business. Never throws: a message whose recipient cannot
     * be resolved, or whose send fails, is logged and counted as failed while the rest go out.
     */
    public Outcome send(List<OwnerMessage> messages) {
        if (messages.isEmpty()) {
            return new Outcome(0, 0);
        }

        Map<String, String> emailsByBusinessId = resolveOwnerEmails(
                messages.stream().map(OwnerMessage::ownerBusinessId).distinct().toList());

        int notified = 0;
        int failed = 0;
        for (OwnerMessage message : messages) {
            String recipient = emailsByBusinessId.get(message.ownerBusinessId());
            if (recipient == null) {
                log.warn("Skipping media-owner notification for business {}: no resolvable email",
                        message.ownerBusinessId());
                failed++;
                continue;
            }
            try {
                emailService.sendSimpleEmail(recipient, message.subject(), message.body());
                notified++;
            } catch (Exception e) {
                log.error("Failed to notify media owner {}", message.ownerBusinessId(), e);
                failed++;
            }
        }
        return new Outcome(notified, failed);
    }

    /**
     * Maps each owner business to one contact address: its first employee carrying a user id,
     * resolved through Auth0. Businesses with no employee, no user id, or no matching Auth0
     * account are simply absent from the result — that is a normal outcome, not an error.
     *
     * <p>Returns an empty map rather than throwing if Auth0 is unreachable, so that a caller in
     * a transaction is not rolled back by a notification concern.
     */
    public Map<String, String> resolveOwnerEmails(Collection<String> ownerBusinessIds) {
        if (ownerBusinessIds.isEmpty()) {
            return Map.of();
        }

        // Insertion-ordered, and the query is ordered by id, so "first employee" is deterministic.
        Map<String, String> userIdByBusinessId = new LinkedHashMap<>();
        for (Employee employee : employeeRepository.findAllByBusinessId_BusinessIdInOrderById(ownerBusinessIds)) {
            String userId = employee.getUserId();
            if (userId == null || userId.isBlank() || employee.getBusinessId() == null) {
                continue;
            }
            userIdByBusinessId.putIfAbsent(employee.getBusinessId().getBusinessId(), userId);
        }
        if (userIdByBusinessId.isEmpty()) {
            return Map.of();
        }

        Map<String, String> emailByUserId;
        try {
            emailByUserId = auth0Service.findEmailsByUserIds(List.copyOf(userIdByBusinessId.values()));
        } catch (Exception e) {
            log.error("Could not resolve media-owner emails from Auth0 for {} business(es); "
                    + "no notifications will be sent for them", userIdByBusinessId.size(), e);
            return Map.of();
        }

        Map<String, String> emailByBusinessId = new LinkedHashMap<>();
        userIdByBusinessId.forEach((businessId, userId) -> {
            String email = emailByUserId.get(userId);
            if (email != null) {
                emailByBusinessId.put(businessId, email);
            }
        });
        return emailByBusinessId;
    }
}
