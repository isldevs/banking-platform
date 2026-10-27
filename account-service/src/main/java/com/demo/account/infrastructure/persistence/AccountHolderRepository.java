package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountHolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolderEntity, AccountHolderEntity.Id> {
}
