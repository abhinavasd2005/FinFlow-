package com.finflow.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferSlipResponse {

    private Long transactionId;
    private String referenceNumber;
    private String status;
    private BigDecimal amount;
    private String fromWalletNumber;
    private String fromWalletName;
    private String fromOwnerUsername;
    private String toWalletNumber;
    private String toWalletName;
    private String toOwnerUsername;
    private BigDecimal balanceAfterTransfer;
    private String description;
    private String failureReason;
    private Integer fraudScore;
    private LocalDateTime createdAt;

    public TransferSlipResponse(Long transactionId,
                                String referenceNumber,
                                String status,
                                BigDecimal amount,
                                String fromWalletNumber,
                                String fromWalletName,
                                String fromOwnerUsername,
                                String toWalletNumber,
                                String toWalletName,
                                String toOwnerUsername,
                                BigDecimal balanceAfterTransfer,
                                String description,
                                String failureReason,
                                Integer fraudScore,
                                LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.referenceNumber = referenceNumber;
        this.status = status;
        this.amount = amount;
        this.fromWalletNumber = fromWalletNumber;
        this.fromWalletName = fromWalletName;
        this.fromOwnerUsername = fromOwnerUsername;
        this.toWalletNumber = toWalletNumber;
        this.toWalletName = toWalletName;
        this.toOwnerUsername = toOwnerUsername;
        this.balanceAfterTransfer = balanceAfterTransfer;
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

    public String getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFromWalletNumber() {
        return fromWalletNumber;
    }

    public String getFromWalletName() {
        return fromWalletName;
    }

    public String getFromOwnerUsername() {
        return fromOwnerUsername;
    }

    public String getToWalletNumber() {
        return toWalletNumber;
    }

    public String getToWalletName() {
        return toWalletName;
    }

    public String getToOwnerUsername() {
        return toOwnerUsername;
    }

    public BigDecimal getBalanceAfterTransfer() {
        return balanceAfterTransfer;
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