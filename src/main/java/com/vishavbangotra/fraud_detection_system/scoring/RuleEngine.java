package com.vishavbangotra.fraud_detection_system.scoring;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.rules.Rule;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
        log.info("RuleEngine initialized with {} rules: {}",
                rules.size(),
                rules.stream().map(Rule::name).toList());
    }

    public ScoredTransaction score(Transaction txn) {
        int total = 0;
        List<String> triggered = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                if (rule.evaluate(txn)) {
                    total += rule.weight();
                    triggered.add(rule.name());
                }
            } catch (Exception e) {
                log.error("Rule {} failed for txn {}: {}", rule.name(),
                        txn != null ? txn.getTransactionId() : "null", e.getMessage(), e);
            }
        }
        int capped = Math.min(total, 100);
        return ScoredTransaction.builder()
                .transaction(txn)
                .score(capped)
                .triggeredRules(triggered)
                .riskLevel(RiskLevel.fromScore(capped))
                .build();
    }
}
