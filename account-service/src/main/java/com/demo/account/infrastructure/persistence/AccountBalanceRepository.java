package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountBalanceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, UUID> {}
