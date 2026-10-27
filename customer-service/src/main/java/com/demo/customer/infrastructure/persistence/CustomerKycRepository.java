package com.demo.customer.infrastructure.persistence;

import com.demo.customer.domain.model.CustomerKyc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerKycRepository extends JpaRepository<CustomerKyc, UUID> {
    Optional<CustomerKyc> findByCustomerId(UUID customerId);
}
