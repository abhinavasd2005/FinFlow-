package com.finflow.dto.response;

import java.math.BigDecimal;

public class AiTransferDraftResponse {

    private Long fromWalletId;
    private String destinationWalletNumber;
    private BigDecimal amount;
    private String description;
    private boolean needsClarification;
    private String message;

    public AiTransferDraftResponse() {
    }

    public AiTransferDraftResponse(
            Long fromWalletId,
            String destinationWalletNumber,
            BigDecimal amount,
            String description,
            boolean needsClarification,
            String message) {
        this.fromWalletId = fromWalletId;
        this.destinationWalletNumber = destinationWalletNumber;
        this.amount = amount;
        this.description = description;
        this.needsClarification = needsClarification;
        this.message = message;
    }

    public Long getFromWalletId() { return fromWalletId; }
    public String getDestinationWalletNumber() { return destinationWalletNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public boolean isNeedsClarification() { return needsClarification; }
    public String getMessage() { return message; }
}
