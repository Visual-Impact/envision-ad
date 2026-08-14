package com.envisionad.webservice.business.presentationlayer;

import com.envisionad.webservice.business.businesslogiclayer.BusinessService;
import com.envisionad.webservice.business.presentationlayer.models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/businesses")
@CrossOrigin(origins = { "http://localhost:3000", "https://envision-ad.ca" })
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    // P5 FR 1.4: this was the self-registration endpoint (any authenticated caller,
    // ownerId derived from their own JWT). Admin-only now — the admin account-creation
    // flow (POST /api/v1/admin/accounts, AdminAccountController) is the real entry
    // point for provisioning a new client's business and does not call this endpoint;
    // it composes the same validation/save logic directly with an explicit ownerId.
    @PostMapping
    @PreAuthorize("hasAuthority('manage:accounts')")
    public ResponseEntity<BusinessResponseModel> createBusiness(@AuthenticationPrincipal Jwt jwt,
            @RequestBody BusinessRequestModel requestModel) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessService.createBusiness(jwt, requestModel));
    }

    @GetMapping
    public ResponseEntity<List<BusinessResponseModel>> getAllBusinesses() {
        return ResponseEntity.ok(businessService.getAllBusinesses());
    }

    @GetMapping("/{businessId}")
    public ResponseEntity<BusinessResponseModel> getBusinessById(@PathVariable String businessId) {
        return ResponseEntity.ok(businessService.getBusinessById(businessId));
    }

    @PutMapping("/{businessId}")
    @PreAuthorize("hasAuthority('update:business')")
    public ResponseEntity<BusinessResponseModel> updateBusinessById(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId, @RequestBody BusinessRequestModel requestModel) {
        return ResponseEntity.ok(businessService.updateBusinessById(jwt, businessId, requestModel));
    }

    @PostMapping("/{businessId}/verifications")
    @PreAuthorize("hasAuthority('create:verification')")
    public ResponseEntity<VerificationResponseModel> requestVerification(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId) {
        return ResponseEntity.ok(businessService.requestVerification(jwt, businessId));
    }

    @GetMapping("/{businessId}/verifications")
    @PreAuthorize("hasAuthority('read:verification')")
    public ResponseEntity<List<VerificationResponseModel>> getAllVerificationsByBusinessId(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String businessId) {
        return ResponseEntity.ok(businessService.getAllVerificationsByBusinessId(jwt, businessId));
    }

    @GetMapping("/verifications")
    @PreAuthorize("hasAuthority('readAll:verification')")
    public ResponseEntity<List<VerificationResponseModel>> getAllVerificationRequests() {
        return ResponseEntity.ok(businessService.getAllVerificationRequests());
    }

    @PatchMapping("/{businessId}/verifications/{verificationId}/approve")
    @PreAuthorize("hasAuthority('update:verification')")
    public ResponseEntity<VerificationResponseModel> approveBusinessVerification(@PathVariable String businessId,
            @PathVariable String verificationId) {
        return ResponseEntity.ok(businessService.approveBusinessVerification(businessId, verificationId));
    }

    @PatchMapping("/{businessId}/verifications/{verificationId}/deny")
    @PreAuthorize("hasAuthority('update:verification')")
    public ResponseEntity<VerificationResponseModel> denyBusinessVerification(@PathVariable String businessId,
            @PathVariable String verificationId, @RequestBody String reason) {
        return ResponseEntity.ok(businessService.denyBusinessVerification(businessId, verificationId, reason));
    }

    @GetMapping("/{businessId}/invites")
    @PreAuthorize("hasAuthority('read:employee')")
    public ResponseEntity<List<InvitationResponseModel>> getAllBusinessInvitations(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId) {
        return ResponseEntity.ok(businessService.getAllInvitationsByBusinessId(jwt, businessId));
    }

    @PostMapping("/{businessId}/invites")
    @PreAuthorize("hasAuthority('create:employee')")
    public ResponseEntity<InvitationResponseModel> createInvitation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId, @RequestBody InvitationRequestModel invitation) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessService.createInvitation(jwt, businessId, invitation));
    }

    @DeleteMapping("/{businessId}/invites/{invitationId}")
    @PreAuthorize("hasAuthority('create:employee')")
    public ResponseEntity<Void> cancelInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable String businessId,
            @PathVariable String invitationId) {
        businessService.cancelInvitation(jwt, businessId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{businessId}/employees")
    @PreAuthorize("hasAuthority('read:employee')")
    public ResponseEntity<List<EmployeeResponseModel>> GetAllBusinessEmployees(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId) {
        return ResponseEntity.ok(businessService.getAllEmployeesByBusinessId(jwt, businessId));
    }

    @PostMapping("/{businessId}/employees")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeResponseModel> addEmployeeToBusiness(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId, @RequestParam String token) {
        return ResponseEntity.ok(businessService.addBusinessEmployee(jwt, businessId, token));
    }

    @DeleteMapping("/{businessId}/employees/{employeeId}")
    @PreAuthorize("hasAuthority('delete:employee')")
    public ResponseEntity<Void> removeEmployeeFromBusiness(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String businessId, @PathVariable String employeeId) {
        businessService.removeBusinessEmployeeById(jwt, businessId, employeeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employee/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BusinessResponseModel> getBusinessByUserId(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {
        return ResponseEntity.ok(businessService.getBusinessByUserId(jwt, userId));
    }
}
