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

  Optional<FundingInstructionEntity> findByChannelAndCurrencyAndExternalReference(
      String channel, String currency, String externalReference);

  // Serialize consumption of a settlement identity, including attempts on different instructions.
  // The unique index remains the final invariant for writers outside this service.
  @Query(
      value =
          "SELECT 1 FROM pg_advisory_xact_lock("
              + "hashtextextended(:channel || ':' || :currency || ':' || :externalReference, 1))",
      nativeQuery = true)
  Integer lockForSettlement(
      @Param("channel") String channel,
      @Param("currency") String currency,
      @Param("externalReference") String externalReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from FundingInstructionEntity f where f.id = :id")
  Optional<FundingInstructionEntity> findByIdForUpdate(@Param("id") UUID id);
}
