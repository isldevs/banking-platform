package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransactionEntity, UUID> {
}
