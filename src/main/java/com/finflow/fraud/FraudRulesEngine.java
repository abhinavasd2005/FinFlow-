package com.finflow.fraud;

import com.finflow.entity.Transaction;
import com.finflow.fraud.FraudScoreCalculator.FraudRuleResult;
import com.finflow.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class FraudRulesEngine {

    private final TransactionRepository transactionRepository;

    private static final Set<BigDecimal> ROUND_NUMBER_FLAGS = Set.of(
            new BigDecimal("500.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("10000.00")
    );

    public FraudRulesEngine(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<FraudRuleResult> evaluate(Transaction transaction) {
        List<FraudRuleResult> results = new ArrayList<>();

        results.add(evaluateLargeAmount(transaction));
        results.add(evaluateVelocity(transaction));
        results.add(evaluateOddHours(transaction));
        results.add(evaluateNewAccount(transaction));
        results.add(evaluateRoundNumber(transaction));

        return results;
    }

    // ── RULE 1: Large amount (>10x wallet average) ────────────────────
    private FraudRuleResult evaluateLargeAmount(Transaction transaction) {
        String rule = "LARGE_AMOUNT";
        try {
            BigDecimal avg = transactionRepository
                    .avgTransactionAmount(transaction.getFromWallet().getId());

            if (avg.compareTo(BigDecimal.ZERO) == 0) {
                return new FraudRuleResult(rule, false, 0, "No prior transactions");
            }

            BigDecimal threshold = avg.multiply(BigDecimal.TEN)
                    .setScale(2, RoundingMode.HALF_UP);

            boolean triggered = transaction.getAmount().compareTo(threshold) > 0;
            return new FraudRuleResult(
                    rule,
                    triggered,
                    triggered ? 30 : 0,
                    triggered
                            ? "Amount " + transaction.getAmount() +
                            " exceeds 10x average " + avg.setScale(2, RoundingMode.HALF_UP)
                            : "Normal amount"
            );
        } catch (Exception e) {
            return new FraudRuleResult(rule, false, 0, "Rule evaluation failed");
        }
    }

    // ── RULE 2: Velocity (>5 transfers in 60 seconds) ─────────────────
    private FraudRuleResult evaluateVelocity(Transaction transaction) {
        String rule = "VELOCITY_CHECK";
        try {
            LocalDateTime since = transaction.getCreatedAt().minusSeconds(60);
            long count = transactionRepository.countRecentTransactions(
                    transaction.getFromWallet().getId(), since);

            boolean triggered = count > 5;
            return new FraudRuleResult(
                    rule,
                    triggered,
                    triggered ? 25 : 0,
                    triggered
                            ? count + " transfers in last 60 seconds"
                            : "Normal velocity"
            );
        } catch (Exception e) {
            return new FraudRuleResult(rule, false, 0, "Rule evaluation failed");
        }
    }

    // ── RULE 3: Odd hours (1AM–4AM and amount > $5000) ────────────────
    private FraudRuleResult evaluateOddHours(Transaction transaction) {
        String rule = "ODD_HOURS";
        try {
            int hour = transaction.getCreatedAt().getHour();
            boolean oddHour = hour >= 1 && hour <= 4;
            boolean largeAmount = transaction.getAmount()
                    .compareTo(new BigDecimal("5000.00")) > 0;

            boolean triggered = oddHour && largeAmount;
            return new FraudRuleResult(
                    rule,
                    triggered,
                    triggered ? 20 : 0,
                    triggered
                            ? "Large transfer at odd hour: " + hour + ":00"
                            : "Normal hours"
            );
        } catch (Exception e) {
            return new FraudRuleResult(rule, false, 0, "Rule evaluation failed");
        }
    }

    // ── RULE 4: New account (<7 days and amount > $1000) ──────────────
    private FraudRuleResult evaluateNewAccount(Transaction transaction) {
        String rule = "NEW_ACCOUNT";
        try {
            LocalDateTime accountCreated = transaction
                    .getFromWallet().getUser().getCreatedAt();
            boolean newAccount = accountCreated
                    .isAfter(LocalDateTime.now().minusDays(7));
            boolean largeAmount = transaction.getAmount()
                    .compareTo(new BigDecimal("1000.00")) > 0;

            boolean triggered = newAccount && largeAmount;
            return new FraudRuleResult(
                    rule,
                    triggered,
                    triggered ? 20 : 0,
                    triggered
                            ? "New account transferring large amount"
                            : "Account age normal"
            );
        } catch (Exception e) {
            return new FraudRuleResult(rule, false, 0, "Rule evaluation failed");
        }
    }

    // ── RULE 5: Round number detection ────────────────────────────────
    private FraudRuleResult evaluateRoundNumber(Transaction transaction) {
        String rule = "ROUND_NUMBER";
        try {
            BigDecimal normalized = transaction.getAmount()
                    .setScale(2, RoundingMode.HALF_UP);
            boolean triggered = ROUND_NUMBER_FLAGS.contains(normalized);
            return new FraudRuleResult(
                    rule,
                    triggered,
                    triggered ? 10 : 0,
                    triggered
                            ? "Suspicious round amount: " + normalized
                            : "Non-round amount"
            );
        } catch (Exception e) {
            return new FraudRuleResult(rule, false, 0, "Rule evaluation failed");
        }
    }
}