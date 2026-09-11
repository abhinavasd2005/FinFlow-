package com.finflow.service;

import com.finflow.async.TransferMetrics;
import com.finflow.concurrency.AccountFreezeManager;
import com.finflow.dto.response.FraudAlertResponse;
import com.finflow.entity.Transaction;
import com.finflow.fraud.FraudQueueProcessor;
import com.finflow.fraud.FraudRulesEngine;
import com.finflow.fraud.FraudScoreCalculator;
import com.finflow.fraud.FraudScoreCalculator.FraudRuleResult;
import com.finflow.repository.FraudAlertRepository;
import com.finflow.repository.TransactionRepository;
import com.finflow.mapper.EntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finflow.entity.FraudAlert;

import java.util.List;

@Service
public class FraudDetectionService {

    private static final Logger log =
            LoggerFactory.getLogger(FraudDetectionService.class);

    private static final int FREEZE_THRESHOLD = 60;

    private final FraudRulesEngine      fraudRulesEngine;
    private final FraudScoreCalculator  fraudScoreCalculator;
    private final FraudQueueProcessor   fraudQueueProcessor;
    private final AccountFreezeManager  accountFreezeManager;
    private final TransferMetrics       transferMetrics;
    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;

    public FraudDetectionService(FraudRulesEngine fraudRulesEngine,
                                 FraudScoreCalculator fraudScoreCalculator,
                                 FraudQueueProcessor fraudQueueProcessor,
                                 AccountFreezeManager accountFreezeManager,
                                 TransferMetrics transferMetrics,
                                 FraudAlertRepository fraudAlertRepository,
                                 TransactionRepository transactionRepository) {

        this.fraudRulesEngine     = fraudRulesEngine;
        this.fraudScoreCalculator = fraudScoreCalculator;
        this.fraudQueueProcessor  = fraudQueueProcessor;
        this.accountFreezeManager = accountFreezeManager;
        this.transferMetrics      = transferMetrics;
        this.fraudAlertRepository=fraudAlertRepository;
        this.transactionRepository = transactionRepository;
    }

    @Async("fraudExecutor")
    @Transactional
    public void analyze(Long transactionId) {
        try {
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found: " + transactionId));
            log.info("[FRAUD] Analyzing transaction: {}", transaction.getId());

            List<FraudRuleResult> results =
                    fraudRulesEngine.evaluate(transaction);

            int score = fraudScoreCalculator.calculate(results);

            log.info("[FRAUD] Transaction {} scored: {}/100",
                    transaction.getId(), score);

            results.stream()
                    .filter(FraudRuleResult::triggered)
                    .forEach(r -> log.warn(
                            "[FRAUD] Rule triggered — {} | Score: {} | {}",
                            r.ruleName(), r.score(), r.reason()));

            fraudQueueProcessor.enqueue(transaction.getId(), score, results);

            if (score >= FREEZE_THRESHOLD) {
                accountFreezeManager.freezeWallet(
                        transaction.getFromWallet().getId(),
                        "Auto-freeze: fraud score " + score);
                log.warn("[FRAUD] Wallet {} auto-frozen | Score: {}",
                        transaction.getFromWallet().getId(), score);
                transferMetrics.recordFraudFlag();
            }

        } catch (Exception e) {
            log.error("[FRAUD] Analysis failed for txn {}: {}",
                    transactionId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<FraudAlertResponse> getAllAlerts() {
        return fraudAlertRepository.findAllWithTransaction()
                .stream()
                .map(EntityMapper::toFraudAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FraudAlertResponse> getAlertsByWallet(Long walletId) {
        return fraudAlertRepository.findByWalletIdWithTransaction(walletId)
                .stream()
                .map(EntityMapper::toFraudAlertResponse)
                .toList();
    }
}
