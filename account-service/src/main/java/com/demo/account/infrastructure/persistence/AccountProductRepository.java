package com.demo.account.infrastructure.persistence;

import com.demo.account.domain.model.AccountProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProductRepository extends JpaRepository<AccountProductEntity, String> {
  Optional<AccountProductEntity> findByProductCodeAndCurrencyAndStatus(
      String productCode, String currency, String status);
}
