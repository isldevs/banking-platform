package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
  List<AccountEntity> findTop100ByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AccountEntity a where a.id = :id")
  java.util.Optional<AccountEntity> findByIdForUpdate(UUID id);
}
