package com.finflow.fraud;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FraudScoreCalculator {

    public int calculate(List<FraudRuleResult> ruleResults) {
        return ruleResults.stream()
                .filter(FraudRuleResult::triggered)
                .mapToInt(FraudRuleResult::score)
                .sum();
    }

    public record FraudRuleResult(
            String ruleName,
            boolean triggered,
            int score,
            String reason
    ) {}
}