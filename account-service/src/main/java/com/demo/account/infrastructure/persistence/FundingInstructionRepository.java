package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.FundingInstructionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FundingInstructionRepository extends JpaRepository<FundingInstructionEntity, UUID> {
    Optional<FundingInstructionEntity> findByIdempotencyKey(String idempotencyKey);
}
