package com.demo.account.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "currencies")
public class CurrencyEntity {
    @Id
    @Column(length = 3)
    private String code;

    @Column(name = "numeric_code", length = 3)
    private String numericCode;

    @Column(name = "minor_units", nullable = false)
    private short minorUnits;

    @Column(nullable = false, length = 20)
    private String status;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNumericCode() { return numericCode; }
    public void setNumericCode(String numericCode) { this.numericCode = numericCode; }
    public short getMinorUnits() { return minorUnits; }
    public void setMinorUnits(short minorUnits) { this.minorUnits = minorUnits; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
