package com.demo.customer.infrastructure.persistence;

import com.demo.customer.domain.model.CustomerStatusHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerStatusHistoryRepository
    extends JpaRepository<CustomerStatusHistory, UUID> {}
