package com.finflow.dto.response;

import com.finflow.enums.WalletStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletResponse {

    private Long id;
    private String walletNumber;
    private String walletName;
    private BigDecimal balance;
    private BigDecimal dailyLimit;
    private WalletStatus status;
    private LocalDateTime createdAt;

    public WalletResponse() {
    }

    public WalletResponse(Long id, String walletNumber, String walletName,
                          BigDecimal balance, BigDecimal dailyLimit,
                          WalletStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.walletNumber = walletNumber;
        this.walletName = walletName;
        this.balance = balance;
        this.dailyLimit = dailyLimit;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getWalletNumber() {
        return walletNumber;
    }

    public String getWalletName() {
        return walletName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setWalletNumber(String walletNumber) {
        this.walletNumber = walletNumber;
    }

    public void setWalletName(String walletName) {
        this.walletName = walletName;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public void setStatus(WalletStatus status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}