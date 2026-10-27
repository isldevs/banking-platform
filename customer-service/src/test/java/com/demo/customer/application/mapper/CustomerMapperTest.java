package com.demo.customer.application.mapper;

import com.demo.customer.api.dto.request.NewCustomer;
import com.demo.customer.api.dto.response.CustomerResponse;
import com.demo.customer.domain.model.Customer;
import com.demo.customer.domain.model.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomerMapperTest {

    private final CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);

    @Test
    void mapsCreateRequestWithoutServerManagedFields() {
        NewCustomer request = new NewCustomer(
                "Sivlay", "Yi", LocalDate.of(1995, 1, 1), "012345678", "sivlay@example.com");

        Customer customer = mapper.toEntity(request);

        assertEquals("Sivlay", customer.getFirstName());
        assertEquals("Yi", customer.getLastName());
        assertEquals(LocalDate.of(1995, 1, 1), customer.getDateOfBirth());
        assertEquals("012345678", customer.getPhone());
        assertEquals("sivlay@example.com", customer.getEmail());
        assertNull(customer.getId());
        assertNull(customer.getCustomerNumber());
        assertNull(customer.getUserId());
        assertNull(customer.getCreatedAt());
        assertNull(customer.getUpdatedAt());
    }

    @Test
    void mapsEntityToApiResponseWithStringStatus() {
        Customer customer = new Customer();
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        customer.setId(id);
        customer.setUserId(userId);
        customer.setCustomerNumber("CUS-ABCDEF123456");
        customer.setFirstName("Sivlay");
        customer.setLastName("Yi");
        customer.setDateOfBirth(LocalDate.of(1995, 1, 1));
        customer.setStatus(CustomerStatus.PENDING_KYC);

        CustomerResponse response = mapper.toResponse(customer);

        assertEquals(id, response.id());
        assertEquals(userId, response.userId());
        assertEquals("CUS-ABCDEF123456", response.customerNumber());
        assertEquals("PENDING_KYC", response.status());
    }
}
