package com.finflow.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class WalletStatementResponse {

    private Long walletId;
    private String walletNumber;
    private String walletName;
    private BigDecimal currentBalance;
    private BigDecimal totalDebited;
    private BigDecimal totalCredited;
    private long totalTransactions;
    private LocalDateTime from;
    private LocalDateTime to;
    private List<TransactionHistoryResponse> transactions;

    public WalletStatementResponse(Long walletId,
                                   String walletNumber,
                                   String walletName,
                                   BigDecimal currentBalance,
                                   BigDecimal totalDebited,
                                   BigDecimal totalCredited,
                                   long totalTransactions,
                                   LocalDateTime from,
                                   LocalDateTime to,
                                   List<TransactionHistoryResponse> transactions) {
        this.walletId = walletId;
        this.walletNumber = walletNumber;
        this.walletName = walletName;
        this.currentBalance = currentBalance;
        this.totalDebited = totalDebited;
        this.totalCredited = totalCredited;
        this.totalTransactions = totalTransactions;
        this.from = from;
        this.to = to;
        this.transactions = transactions;
    }

    public Long getWalletId() {
        return walletId;
    }

    public String getWalletNumber() {
        return walletNumber;
    }

    public String getWalletName() {
        return walletName;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getTotalDebited() {
        return totalDebited;
    }

    public BigDecimal getTotalCredited() {
        return totalCredited;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public List<TransactionHistoryResponse> getTransactions() {
        return transactions;
    }
}