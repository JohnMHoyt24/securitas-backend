package com.securitas.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_account_id", nullable = false)
    private String fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private String toAccountId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "payment_format")
    private String paymentFormat;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "is_laundering", nullable = false)
    private boolean laundering;

    protected Transaction() {
    }

    public Transaction(String fromAccountId, String toAccountId, BigDecimal amount, String currency,
                        String paymentFormat, OffsetDateTime occurredAt, boolean laundering) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.paymentFormat = paymentFormat;
        this.occurredAt = occurredAt;
        this.laundering = laundering;
    }

    public Long getId() {
        return id;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentFormat() {
        return paymentFormat;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public boolean isLaundering() {
        return laundering;
    }
}
