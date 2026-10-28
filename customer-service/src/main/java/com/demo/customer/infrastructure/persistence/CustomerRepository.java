package com.demo.customer.infrastructure.persistence;

import com.demo.customer.domain.model.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {}
