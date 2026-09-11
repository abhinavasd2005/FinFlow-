package com.finflow.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class FinancialSummaryResponse {

    private Long walletId;
    private LocalDateTime from;
    private LocalDateTime to;
    private String summary;
    private List<String> highlights;
    private List<String> recommendations;

    public FinancialSummaryResponse() {
    }

    public FinancialSummaryResponse(
            Long walletId,
            LocalDateTime from,
            LocalDateTime to,
            String summary,
            List<String> highlights,
            List<String> recommendations) {
        this.walletId = walletId;
        this.from = from;
        this.to = to;
        this.summary = summary;
        this.highlights = highlights;
        this.recommendations = recommendations;
    }

    public Long getWalletId() { return walletId; }
    public LocalDateTime getFrom() { return from; }
    public LocalDateTime getTo() { return to; }
    public String getSummary() { return summary; }
    public List<String> getHighlights() { return highlights; }
    public List<String> getRecommendations() { return recommendations; }
}
