package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.CurrencyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<CurrencyEntity, String> {
  Optional<CurrencyEntity> findByCodeAndStatus(String code, String status);
}
