package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountOpeningRequestEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountOpeningRequestRepository
    extends JpaRepository<AccountOpeningRequestEntity, UUID> {
  // PostgreSQL releases this advisory lock at transaction completion.
  // A competing request using the same principal/key waits before reading prior state.
  @Query(
      value =
          "SELECT 1 FROM pg_advisory_xact_lock("
              + "hashtextextended(CAST(:principalId AS text) || ':' || :clientKey, 0))",
      nativeQuery = true)
  Integer lockForOpening(
      @Param("principalId") UUID principalId, @Param("clientKey") String clientKey);

  Optional<AccountOpeningRequestEntity> findByPrincipalIdAndIdempotencyKey(
      UUID principalId, String idempotencyKey);
}
