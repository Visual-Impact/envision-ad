package com.envisionad.webservice.admin.businesslogiclayer;

import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountListItemModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountRequestModel;
import com.envisionad.webservice.admin.presentationlayer.models.AdminAccountResponseModel;
import com.envisionad.webservice.business.presentationlayer.models.BusinessResponseModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAccountService {

    AdminAccountResponseModel createAccount(AdminAccountRequestModel request);

    void resendCredentials(String businessId);

    BusinessResponseModel setActive(String businessId, boolean active);

    Page<AdminAccountListItemModel> getAllAccounts(Pageable pageable);
}
