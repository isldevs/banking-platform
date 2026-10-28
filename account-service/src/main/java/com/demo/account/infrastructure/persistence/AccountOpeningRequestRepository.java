package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountOpeningRequestEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOpeningRequestRepository
    extends JpaRepository<AccountOpeningRequestEntity, UUID> {
  Optional<AccountOpeningRequestEntity> findByPrincipalIdAndIdempotencyKey(
      UUID principalId, String idempotencyKey);
}
