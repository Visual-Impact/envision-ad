package com.envisionad.webservice.admin.presentationlayer;

import com.envisionad.webservice.admin.businesslogiclayer.AdminAccountService;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountListItemModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountRequestModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountResponseModel;
import com.envisionad.webservice.admin.presentationlayer.models.SetActiveRequestModel;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@CrossOrigin(origins = { "http://localhost:3000", "https://envision-ad.ca" })
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage:accounts')")
    public ResponseEntity<AdminAccountResponseModel> createAccount(@RequestBody AdminAccountRequestModel request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAccountService.createAccount(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('manage:accounts')")
    public ResponseEntity<List<AdminAccountListItemModel>> getAllAccounts() {
        return ResponseEntity.ok(adminAccountService.getAllAccounts());
    }

    @PostMapping("/{businessId}/resend-credentials")
    @PreAuthorize("hasAuthority('manage:accounts')")
    public ResponseEntity<Void> resendCredentials(@PathVariable String businessId) {
        adminAccountService.resendCredentials(businessId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{businessId}/active")
    @PreAuthorize("hasAuthority('manage:accounts')")
    public ResponseEntity<BusinessResponseModel> setActive(@PathVariable String businessId,
            @RequestBody SetActiveRequestModel request) {
        return ResponseEntity.ok(adminAccountService.setActive(businessId, request.isActive()));
    }
}
