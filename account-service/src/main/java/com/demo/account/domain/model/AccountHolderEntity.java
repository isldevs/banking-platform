package com.demo.account.domain.model;

import jakarta.persistence.Column;
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

    public Id getId() { return id; }
    public void setId(Id id) { this.id = id; }
    public String getHolderType() { return holderType; }
    public void setHolderType(String holderType) { this.holderType = holderType; }

    public record Id(@Column(name = "account_id") UUID accountId,
                     @Column(name = "customer_id") UUID customerId) implements Serializable {}
}
