package com.demo.customer.application.mapper;

import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.api.dto.response.CustomerResponse;
import com.demo.customer.domain.model.Customer;
import com.demo.customer.domain.model.CustomerStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerNumber", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(NewCustomer request);

    @Mapping(target = "status", source = "status")
    CustomerResponse toResponse(Customer customer);

    default String map(CustomerStatus status) {
        return status == null ? null : status.name();
    }
}
