package com.demo.customer.infrastructure.persistence;

import com.demo.customer.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}
