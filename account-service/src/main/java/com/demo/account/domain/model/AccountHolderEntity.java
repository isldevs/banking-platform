package com.demo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "account_holders")
public class AccountHolderEntity {
    @EmbeddedId
    private Id id;
    @Column(name = "holder_type", nullable = false, length = 30)
    private String holderType = "PRIMARY";

    public Id getId() {
        return id;
    }

    public void setId(Id id) {
        this.id = id;
    }

    public String getHolderType() {
        return holderType;
    }

    public void setHolderType(String holderType) {
        this.holderType = holderType;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "account_id")
        private UUID accountId;
        @Column(name = "customer_id")
        private UUID customerId;

        protected Id() {
        }

        public Id(UUID accountId, UUID customerId) {
            this.accountId = accountId;
            this.customerId = customerId;
        }

        public UUID getAccountId() {
            return accountId;
        }

        public UUID getCustomerId() {
            return customerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Id other))
                return false;
            return java.util.Objects.equals(accountId, other.accountId) && java.util.Objects.equals(customerId, other.customerId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(accountId, customerId);
        }
    }
}
