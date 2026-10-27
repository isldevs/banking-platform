package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountOperationRepository extends JpaRepository<AccountOperationEntity, UUID> {
}
