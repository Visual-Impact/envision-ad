package com.envisionad.webservice.admin.businesslogiclayer;

import com.envisionad.webservice.admin.exceptions.DuplicateAccountException;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountListItemModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountRequestModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountResponseModel;
import com.envisionad.webservice.admin.presentationlayer.models.RoleRemovalEligibilityResponseModel;
import com.envisionad.webservice.admin.presentationlayer.models.UpdateRolesResponseModel;
import com.envisionad.webservice.business.dataaccesslayer.Business;
import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.BusinessRepository;
import com.envisionad.webservice.business.dataaccesslayer.Employee;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeIdentifier;
import com.envisionad.webservice.business.dataaccesslayer.EmployeeRepository;
import com.envisionad.webservice.business.dataaccesslayer.Roles;
import com.envisionad.webservice.business.exceptions.AdvertiserRoleRemovalBlockedException;
import com.envisionad.webservice.business.exceptions.BadBusinessRequestException;
import com.envisionad.webservice.business.exceptions.BusinessNotFoundException;
import com.envisionad.webservice.business.exceptions.DuplicateBusinessNameException;
import com.envisionad.webservice.business.exceptions.MediaOwnerRoleRemovalBlockedException;
import com.envisionad.webservice.business.mappinglayer.BusinessMapper;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import com.envisionad.webservice.business.utils.Validator;
import com.envisionad.webservice.config.Auth0Roles;
import com.envisionad.webservice.config.Auth0Service;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionItemRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionRepository;
import com.envisionad.webservice.payment.dataaccesslayer.BundleSubscriptionStatus;
import com.envisionad.webservice.utils.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the admin "create client account" flow (brief FR 4.2): Auth0 user →
 * Business + Employee → role assignment + password-change ticket + welcome email.
 * The whole method runs inside one @Transactional boundary — matching
 * CouponServiceImpl's existing external-API-plus-DB pattern in this codebase — but
 * every step-(d) failure (role assignment, ticket, email) is caught and turned into a
 * warning rather than rethrown, so it can never trigger a rollback of the already-good
 * Business/Employee rows. That's what makes those steps "best-effort after the DB
 * commit" (FR 4.2.d) even though they execute before the physical commit completes.
 */
@Slf4j
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    @Value("${app.base.url}")
    private String appBaseUrl;

    /** The "live" bundle-subscription statuses this service blocks role removal on — matches the
     * set every other guard in this codebase uses (e.g. AdCampaignServiceImpl, ProofOfDisplayService),
     * not the wider INCOMPLETE-inclusive set BundleSubscriptionStatus's javadoc describes for the
     * duplicate-subscription unique index. INCOMPLETE means checkout was started but never
     * confirmed by Stripe — not a live commercial commitment yet. */
    private static final List<BundleSubscriptionStatus> LIVE_SUBSCRIPTION_STATUSES =
            List.of(BundleSubscriptionStatus.ACTIVE, BundleSubscriptionStatus.PAST_DUE);

    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final BundleSubscriptionRepository bundleSubscriptionRepository;
    private final BundleSubscriptionItemRepository bundleSubscriptionItemRepository;
    private final BusinessMapper businessMapper;
    private final Auth0Service auth0Service;
    private final EmailService emailService;

    public AdminAccountServiceImpl(BusinessRepository businessRepository, EmployeeRepository employeeRepository,
            BundleSubscriptionRepository bundleSubscriptionRepository,
            BundleSubscriptionItemRepository bundleSubscriptionItemRepository,
            BusinessMapper businessMapper, Auth0Service auth0Service, EmailService emailService) {
        this.businessRepository = businessRepository;
        this.employeeRepository = employeeRepository;
        this.bundleSubscriptionRepository = bundleSubscriptionRepository;
        this.bundleSubscriptionItemRepository = bundleSubscriptionItemRepository;
        this.businessMapper = businessMapper;
        this.auth0Service = auth0Service;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public AdminAccountResponseModel createAccount(AdminAccountRequestModel request) {
        Validator.validateBusiness(request.getBusiness());

        // FR 4.2.a — Auth0 is the only source of truth for email; the local schema has
        // no email column to check against (P5-PROGRESS.md ground truth item 2).
        if (auth0Service.findUserIdByEmail(request.getEmail()).isPresent())
            throw new DuplicateAccountException(request.getEmail());

        if (businessRepository.existsByNameAndBusinessId_BusinessIdNot(request.getBusiness().getName(), null))
            throw new DuplicateBusinessNameException();

        String userId = auth0Service.createUser(request.getEmail(), request.getName());

        Business business;
        try {
            business = businessMapper.toEntity(request.getBusiness());
            business.setBusinessId(new BusinessIdentifier());
            business.setOwnerId(userId);
            business.setVerified(true);
            business.setActive(true);

            Employee employee = new Employee();
            employee.setEmployeeId(new EmployeeIdentifier());
            employee.setBusinessId(business.getBusinessId());
            employee.setUserId(userId);

            businessRepository.save(business);
            employeeRepository.save(employee);
        } catch (RuntimeException dbFailure) {
            // FR 4.2.c — compensate the just-created Auth0 user; the DB half rolls back
            // on its own via @Transactional.
            log.error("Admin account creation failed after Auth0 user {} was created; compensating with delete",
                    userId, dbFailure);
            auth0Service.deleteUser(userId);
            throw dbFailure;
        }

        List<String> warnings = new ArrayList<>();
        assignRolesBestEffort(userId, business.getRoles(), warnings);
        String ticketUrl = createPasswordTicketBestEffort(userId, warnings);
        sendWelcomeEmailBestEffort(request.getEmail(), request.getName(), business.getName(), ticketUrl, warnings);

        AdminAccountResponseModel response = new AdminAccountResponseModel();
        response.setBusiness(businessMapper.toResponse(business));
        response.setOwnerUserId(userId);
        response.setWarnings(warnings);
        return response;
    }

    @Override
    public void resendCredentials(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        String ticketUrl = auth0Service.createPasswordChangeTicket(business.getOwnerId(), appBaseUrl + "/auth/login");
        String email = auth0Service.getUserEmailByUserId(business.getOwnerId());

        String subject = "Your Envision Ad account is ready";
        String body = "Hi there,\n\n"
                + "Here's a fresh link to set your password for " + business.getName() + " on Envision Ad:\n"
                + ticketUrl + "\n\n"
                + "Once set, log in here:\n" + appBaseUrl + "/auth/login\n\n"
                + "— The Envision Ad Team";
        emailService.sendSimpleEmail(email, subject, body);
    }

    @Override
    public BusinessResponseModel setActive(String businessId, boolean active) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        business.setActive(active);
        businessRepository.save(business);
        // Mirrors to Auth0 after the local write (FR 4.4/5.5). No compensation on Auth0
        // failure here — the admin table already reflects the new state and the action
        // is idempotent, so retrying this same call is the recovery path.
        auth0Service.setUserBlocked(business.getOwnerId(), !active);

        return businessMapper.toResponse(business);
    }

    /**
     * PATCH /api/v1/admin/accounts/{businessId}/roles. Replaces the client-facing
     * PUT /businesses/{businessId} as the only way a business's MEDIA_OWNER/ADVERTISER
     * flags can change post-creation — that endpoint used to allow this too (any
     * employee with update:business, no dependent-data checks, no Auth0 resync); it now
     * always preserves the business's existing roles regardless of payload (see
     * BusinessServiceImpl.updateBusinessById).
     * <p>
     * Removing a role blocks on live commercial commitment, not mere existence — see
     * {@link MediaOwnerRoleRemovalBlockedException} and
     * {@link AdvertiserRoleRemovalBlockedException} for exactly what's checked and why.
     * Adding a role has no blockers. Dropping to zero roles is rejected the same way
     * account creation already rejects it (Validator.validateRoles's rule) — deactivation
     * (PATCH .../active) is the intended operation for "this account shouldn't function
     * at all," not an empty roles set.
     */
    @Override
    @Transactional
    public UpdateRolesResponseModel updateRoles(String businessId, Roles requestedRoles) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        if (!requestedRoles.isMediaOwner() && !requestedRoles.isAdvertiser())
            throw new BadBusinessRequestException(
                    "A business must hold at least one role. To disable this account entirely, "
                            + "use PATCH /api/v1/admin/accounts/" + businessId + "/active instead.");

        Roles currentRoles = business.getRoles();

        if (currentRoles.isMediaOwner() && !requestedRoles.isMediaOwner() && hasLiveMediaOwnerSubscription(businessId))
            throw new MediaOwnerRoleRemovalBlockedException(businessId);

        if (currentRoles.isAdvertiser() && !requestedRoles.isAdvertiser()) {
            long liveSubscriptionCount = countLiveAdvertiserSubscriptions(businessId);
            if (liveSubscriptionCount > 0)
                throw new AdvertiserRoleRemovalBlockedException(businessId, liveSubscriptionCount);
        }

        currentRoles.setMediaOwner(requestedRoles.isMediaOwner());
        currentRoles.setAdvertiser(requestedRoles.isAdvertiser());
        businessRepository.save(business);

        List<String> warnings = new ArrayList<>();
        resyncEmployeeAuth0Roles(businessId, currentRoles, warnings);

        UpdateRolesResponseModel response = new UpdateRolesResponseModel();
        response.setBusiness(businessMapper.toResponse(business));
        response.setWarnings(warnings);
        return response;
    }

    /**
     * GET /api/v1/admin/accounts/{businessId}/roles/removal-eligibility — read-only
     * precheck for the roles-edit UI. Lets the admin modal know *before* the admin hits
     * save whether removing a currently-held role would be blocked, so the UI can gray
     * out the save action and explain why, instead of the admin discovering the block
     * only after submitting and getting a 409. Uses the exact same guard predicates as
     * {@link #updateRoles}, so this can never say "removable" when updateRoles would
     * then reject it.
     */
    @Override
    public RoleRemovalEligibilityResponseModel getRoleRemovalEligibility(String businessId) {
        Business business = businessRepository.findByBusinessId_BusinessId(businessId);
        if (business == null)
            throw new BusinessNotFoundException(businessId);

        Roles roles = business.getRoles();
        boolean mediaOwnerRemovable = !roles.isMediaOwner() || !hasLiveMediaOwnerSubscription(businessId);
        boolean advertiserRemovable = !roles.isAdvertiser() || countLiveAdvertiserSubscriptions(businessId) == 0;

        RoleRemovalEligibilityResponseModel response = new RoleRemovalEligibilityResponseModel();
        response.setMediaOwnerRemovable(mediaOwnerRemovable);
        response.setAdvertiserRemovable(advertiserRemovable);
        return response;
    }

    private boolean hasLiveMediaOwnerSubscription(String businessId) {
        return bundleSubscriptionItemRepository.existsLiveSubscriptionForMediaOwnerBusinessId(
                businessId, LIVE_SUBSCRIPTION_STATUSES);
    }

    private long countLiveAdvertiserSubscriptions(String businessId) {
        return bundleSubscriptionRepository.countByAdvertiserBusinessIdAndStatusIn(businessId, LIVE_SUBSCRIPTION_STATUSES);
    }

    /**
     * Fans the business's new role set out to every employee's Auth0 roles — not just
     * the owner's. Auth0 role grants are per-user, assigned at invite-accept time from
     * whatever the business's roles were *then* (BusinessServiceImpl.addBusinessEmployee);
     * nothing re-syncs them later, so a business-level role change has to walk every
     * Employee row itself.
     * <p>
     * Always re-applies the full target state (assign what should be held, remove what
     * shouldn't) rather than computing a diff from the old roles — both Auth0 calls are
     * idempotent, so this makes the whole operation safe to retry after a partial
     * failure without tracking which employees already got resynced.
     */
    private void resyncEmployeeAuth0Roles(String businessId, Roles newRoles, List<String> warnings) {
        List<String> toAssign = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();
        (newRoles.isAdvertiser() ? toAssign : toRemove).add(Auth0Roles.ADVERTISER);
        (newRoles.isMediaOwner() ? toAssign : toRemove).add(Auth0Roles.MEDIA_OWNER);

        boolean anyFailed = false;
        for (Employee employee : employeeRepository.findAllByBusinessId_BusinessId(businessId)) {
            try {
                if (!toAssign.isEmpty())
                    auth0Service.assignRoles(employee.getUserId(), toAssign);
                if (!toRemove.isEmpty())
                    auth0Service.removeRoles(employee.getUserId(), toRemove);
            } catch (RuntimeException e) {
                log.warn("Auth0 role resync failed for employee {} of business {}",
                        employee.getUserId(), businessId, e);
                anyFailed = true;
            }
        }
        if (anyFailed)
            warnings.add("Role sync may not have completed for one or more employees. "
                    + "Retrying this same role update is safe and will re-sync everyone.");
    }

    @Override
    public Page<AdminAccountListItemModel> getAllAccounts(Pageable pageable) {
        Page<Business> businessPage = businessRepository.findAll(pageable);
        List<String> ownerIds = businessPage.getContent().stream()
                .map(Business::getOwnerId).distinct().toList();
        Map<String, String> emailsByOwnerId = safeFindEmails(ownerIds);

        return businessPage.map(business -> {
            AdminAccountListItemModel item = new AdminAccountListItemModel();
            item.setBusinessId(business.getBusinessId().getBusinessId());
            item.setName(business.getName());
            item.setOwnerEmail(emailsByOwnerId.get(business.getOwnerId()));
            item.setRoles(business.getRoles());
            item.setBusinessTypeVenueId(business.getBusinessTypeVenueId());
            item.setActive(business.isActive());
            item.setDateCreated(business.getDateCreated());
            return item;
        });
    }

    // One failed batch lookup shouldn't 500 the whole page — degrade every row's owner
    // email for this load rather than failing the list (D8's original per-row intent,
    // now scoped to one failure point per page instead of N).
    private Map<String, String> safeFindEmails(List<String> ownerIds) {
        try {
            return auth0Service.findEmailsByUserIds(ownerIds);
        } catch (RuntimeException e) {
            log.warn("Could not resolve owner emails for {} business owner(s)", ownerIds.size(), e);
            return Map.of();
        }
    }

    private void assignRolesBestEffort(String userId, Roles roles, List<String> warnings) {
        try {
            List<String> roleIds = new ArrayList<>();
            roleIds.add(Auth0Roles.BUSINESS_OWNER);
            if (roles.isAdvertiser())
                roleIds.add(Auth0Roles.ADVERTISER);
            if (roles.isMediaOwner())
                roleIds.add(Auth0Roles.MEDIA_OWNER);
            auth0Service.assignRoles(userId, roleIds);
        } catch (RuntimeException e) {
            log.warn("Role assignment failed for newly created admin account, user {}", userId, e);
            warnings.add("Role assignment may not have completed. Use Resend Credentials or contact support if the client reports missing permissions.");
        }
    }

    private String createPasswordTicketBestEffort(String userId, List<String> warnings) {
        try {
            return auth0Service.createPasswordChangeTicket(userId, appBaseUrl + "/auth/login");
        } catch (RuntimeException e) {
            log.warn("Password-change ticket creation failed for user {}", userId, e);
            warnings.add("Password-change link could not be generated. Use Resend Credentials to retry.");
            return null;
        }
    }

    private void sendWelcomeEmailBestEffort(String email, String name, String businessName, String ticketUrl,
            List<String> warnings) {
        if (ticketUrl == null) {
            warnings.add("Welcome email not sent — no password-change link was available.");
            return;
        }
        try {
            String subject = "Your Envision Ad account is ready";
            String body = "Hi " + (name != null && !name.isBlank() ? name : "there") + ",\n\n"
                    + "An Envision Ad account has been created for " + businessName + ".\n\n"
                    + "Set your password to get started:\n" + ticketUrl + "\n\n"
                    + "Once set, log in here:\n" + appBaseUrl + "/auth/login\n\n"
                    + "— The Envision Ad Team";
            emailService.sendSimpleEmail(email, subject, body);
        } catch (RuntimeException e) {
            log.warn("Welcome email failed to send to {}", email, e);
            warnings.add("Welcome email may not have sent. Use Resend Credentials to retry.");
        }
    }
}
