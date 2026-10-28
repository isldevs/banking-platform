package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountTransactionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionRepository
    extends JpaRepository<AccountTransactionEntity, UUID> {}
