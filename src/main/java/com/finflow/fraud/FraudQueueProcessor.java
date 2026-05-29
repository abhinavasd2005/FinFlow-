package com.finflow.fraud;

import com.finflow.entity.FraudAlert;
import com.finflow.entity.Transaction;
import com.finflow.repository.FraudAlertRepository;
import com.finflow.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
public class FraudQueueProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(FraudQueueProcessor.class);

    private static final int QUEUE_CAPACITY   = 1000;
    private static final int FRAUD_THRESHOLD  = 30;

    private final BlockingQueue<FraudQueueItem> fraudQueue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread processorThread;

    public FraudQueueProcessor(FraudAlertRepository fraudAlertRepository,
                               TransactionRepository transactionRepository) {
        this.fraudAlertRepository  = fraudAlertRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostConstruct
    public void startProcessor() {
        processorThread = new Thread(this::processQueue, "FinFlow-FraudQueue");
        processorThread.setDaemon(true);
        processorThread.start();
        log.info("[FRAUD QUEUE] Processor started");
    }

    @PreDestroy
    public void stopProcessor() {
        running.set(false);
        processorThread.interrupt();
        log.info("[FRAUD QUEUE] Processor stopped");
    }

    public boolean enqueue(Transaction transaction,
                           int fraudScore,
                           List<FraudScoreCalculator.FraudRuleResult> results) {
        if (fraudScore < FRAUD_THRESHOLD) return false;

        String reasons = results.stream()
                .filter(FraudScoreCalculator.FraudRuleResult::triggered)
                .map(FraudScoreCalculator.FraudRuleResult::reason)
                .collect(Collectors.joining(" | "));

        boolean offered = fraudQueue.offer(
                new FraudQueueItem(transaction, fraudScore, reasons));

        if (offered) {
            log.warn("[FRAUD QUEUE] Enqueued txn {} | Score: {} | Reasons: {}",
                    transaction.getId(), fraudScore, reasons);
        } else {
            log.error("[FRAUD QUEUE] Queue full — dropped txn {}", transaction.getId());
        }
        return offered;
    }

    private void processQueue() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                FraudQueueItem item = fraudQueue.take();
                processItem(item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[FRAUD QUEUE] Error processing item: {}", e.getMessage());
            }
        }
    }

    private void processItem(FraudQueueItem item) {
        try {
            FraudAlert alert = new FraudAlert();

            alert.setTransaction(item.transaction());

            alert.setReason(item.reasons());

            alert.setTriggeredRules(item.reasons());

            alert.setNotes(
                    "Fraud score " +
                            item.fraudScore() +
                            " triggered suspicious activity"
            );

            alert.setFraudScore(item.fraudScore());

            alert.setStatus(FraudAlert.AlertStatus.PENDING);

            fraudAlertRepository.save(alert);

            Transaction tx = item.transaction();

            tx.setFraudScore(item.fraudScore());

            transactionRepository.save(tx);

            log.warn(
                    "[FRAUD QUEUE] Alert created for txn {} | Score: {}",
                    tx.getId(),
                    item.fraudScore()
            );

        } catch (Exception e) {

            log.error(
                    "[FRAUD QUEUE] Failed to persist alert: {}",
                    e.getMessage()
            );
        }
    }

    public int getQueueSize() {
        return fraudQueue.size();
    }

    public record FraudQueueItem(
            Transaction transaction,
            int fraudScore,
            String reasons
    ) {}
}