package com.finflow.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class FinancialSummaryRequest {

    @NotNull
    private Long walletId;

    @NotNull
    private LocalDateTime from;

    @NotNull
    private LocalDateTime to;

    public Long getWalletId() { return walletId; }
    public void setWalletId(Long walletId) { this.walletId = walletId; }
    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
}
