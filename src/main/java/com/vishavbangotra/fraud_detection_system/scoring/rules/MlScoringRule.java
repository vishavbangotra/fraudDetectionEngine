package com.vishavbangotra.fraud_detection_system.scoring.rules;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.ml.MlScoreResponse;
import com.vishavbangotra.fraud_detection_system.scoring.ml.MlScoringClient;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(prefix = "fraud.rules.ml", name = "enabled", havingValue = "true")
@Slf4j
public class MlScoringRule implements Rule {

    private final MlScoringClient client;
    private final double threshold;
    private final int weight;

    public MlScoringRule(
            MlScoringClient client,
            @Value("${fraud.rules.ml.threshold:0.75}") double threshold,
            @Value("${fraud.rules.ml.weight:35}") int weight) {
        this.client = client;
        this.threshold = threshold;
        this.weight = weight;
    }

    @Override
    public String name() {
        return "ML_SCORING";
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public boolean evaluate(Transaction txn) {
        if (txn == null) {
            return false;
        }

        try {
            Optional<MlScoreResponse> response = client.score(txn);
            boolean fired = response
                    .map(score -> score.riskScore() >= threshold)
                    .orElse(false);
            response.ifPresent(score -> log.debug(
                    "ML score for txn {}: riskScore={}, anomaly={}, fired={}",
                    txn.getTransactionId(), score.riskScore(), score.anomaly(), fired));
            return fired;
        } catch (RuntimeException e) {
            log.warn("ML scoring rule failed open for txn {}: {}",
                    txn.getTransactionId(), e.getMessage());
            return false;
        }
    }
}
