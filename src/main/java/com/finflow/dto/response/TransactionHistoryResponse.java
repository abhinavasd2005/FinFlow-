package com.finflow.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionHistoryResponse {

    private Long transactionId;
    private String referenceNumber;
    private String type;
    private String status;
    private BigDecimal amount;
    private Long fromWalletId;
    private String fromWalletNumber;
    private Long toWalletId;
    private String toWalletNumber;
    private String description;
    private String failureReason;
    private Integer fraudScore;
    private LocalDateTime createdAt;

    public TransactionHistoryResponse(Long transactionId,
                                      String referenceNumber,
                                      String type,
                                      String status,
                                      BigDecimal amount,
                                      Long fromWalletId,
                                      String fromWalletNumber,
                                      Long toWalletId,
                                      String toWalletNumber,
                                      String description,
                                      String failureReason,
                                      Integer fraudScore,
                                      LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.referenceNumber = referenceNumber;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.fromWalletId = fromWalletId;
        this.fromWalletNumber = fromWalletNumber;
        this.toWalletId = toWalletId;
        this.toWalletNumber = toWalletNumber;
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

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getFromWalletId() {
        return fromWalletId;
    }

    public String getFromWalletNumber() {
        return fromWalletNumber;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public String getToWalletNumber() {
        return toWalletNumber;
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