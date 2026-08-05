package com.envisionad.webservice.bundle.mappinglayer;

import com.envisionad.webservice.bundle.dataaccesslayer.Bundle;
import com.envisionad.webservice.bundle.presentationlayer.models.BundleRequestModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BundleRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bundleId", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Bundle requestModelToEntity(BundleRequestModel request);
}
