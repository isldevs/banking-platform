package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.FundingInstructionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundingInstructionRepository
    extends JpaRepository<FundingInstructionEntity, UUID> {
  Optional<FundingInstructionEntity> findByIdempotencyKey(String idempotencyKey);

  Optional<FundingInstructionEntity> findTopByAccountIdOrderByCreatedAtDesc(UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FundingInstructionEntity f where f.id = :id")
  Optional<FundingInstructionEntity> findByIdForUpdate(@Param("id") UUID id);
}
