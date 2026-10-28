package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.LedgerEntryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
  List<LedgerEntryEntity> findByTransactionIdOrderByEntryType(UUID transactionId);
}
