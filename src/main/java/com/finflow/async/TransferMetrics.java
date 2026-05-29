package com.finflow.async;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TransferMetrics {

    private final AtomicLong totalTransfers = new AtomicLong(0);
    private final AtomicLong successfulTransfers = new AtomicLong(0);
    private final AtomicLong failedTransfers = new AtomicLong(0);
    private final AtomicLong fraudFlagged = new AtomicLong(0);
    private final AtomicLong totalAmountCents = new AtomicLong(0);

    public void recordSuccess(BigDecimal amount) {
        totalTransfers.incrementAndGet();
        successfulTransfers.incrementAndGet();
        totalAmountCents.addAndGet(toCents(amount));
    }

    public void recordFailure() {
        totalTransfers.incrementAndGet();
        failedTransfers.incrementAndGet();
    }

    public void recordFraudFlag() {
        fraudFlagged.incrementAndGet();
    }

    public long getTotalTransfers() {
        return totalTransfers.get();
    }

    public long getSuccessfulTransfers() {
        return successfulTransfers.get();
    }

    public long getFailedTransfers() {
        return failedTransfers.get();
    }

    public long getFraudFlagged() {
        return fraudFlagged.get();
    }

    public BigDecimal getTotalAmountTransferred() {
        return BigDecimal.valueOf(totalAmountCents.get(), 2);
    }

    private long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValue();
    }
}