package com.finflow.dto.response;

import com.finflow.enums.TransactionStatus;
import com.finflow.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferResponse {

    private Long transactionId;
    private String referenceNumber;
    private TransactionType transactionType;
    private TransactionStatus status;
    private BigDecimal amount;
    private Long fromWalletId;
    private Long toWalletId;
    private String description;
    private String failureReason;
    private Integer fraudScore;
    private LocalDateTime createdAt;

    public TransferResponse() {
    }

    public TransferResponse(Long transactionId, String referenceNumber, TransactionType transactionType,
                            TransactionStatus status, BigDecimal amount, Long fromWalletId,
                            Long toWalletId, String description, String failureReason,
                            Integer fraudScore, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.referenceNumber = referenceNumber;
        this.transactionType = transactionType;
        this.status = status;
        this.amount = amount;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.description = description;
        this.failureReason = failureReason;
        this.fraudScore = fraudScore;
        this.createdAt = createdAt;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getFromWalletId() {
        return fromWalletId;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public String getDescription() {
        return description;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getFraudScore() {
        return fraudScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}