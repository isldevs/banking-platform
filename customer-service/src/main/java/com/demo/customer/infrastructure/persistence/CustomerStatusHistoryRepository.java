package com.demo.customer.infrastructure.persistence;

import com.demo.customer.domain.model.CustomerStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerStatusHistoryRepository extends JpaRepository<CustomerStatusHistory, UUID> {
}
