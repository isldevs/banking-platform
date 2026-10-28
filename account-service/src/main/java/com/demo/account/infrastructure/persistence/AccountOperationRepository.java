package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountOperationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOperationRepository extends JpaRepository<AccountOperationEntity, UUID> {}
