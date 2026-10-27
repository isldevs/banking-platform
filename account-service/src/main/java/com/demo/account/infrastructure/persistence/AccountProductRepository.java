package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountProductRepository extends JpaRepository<AccountProductEntity, String> {
    Optional<AccountProductEntity> findByProductCodeAndCurrencyAndStatus(String productCode, String currency, String status);
}
