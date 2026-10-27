package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.CurrencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<CurrencyEntity, String> {
    Optional<CurrencyEntity> findByCodeAndStatus(String code, String status);
}
