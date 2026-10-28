package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountOpeningRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOpeningRequestRepository
    extends JpaRepository<AccountOpeningRequestEntity, String> {}
