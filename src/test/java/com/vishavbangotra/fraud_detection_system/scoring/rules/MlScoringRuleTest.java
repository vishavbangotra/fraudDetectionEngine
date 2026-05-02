package com.vishavbangotra.fraud_detection_system.scoring.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.ml.MlScoreResponse;
import com.vishavbangotra.fraud_detection_system.scoring.ml.MlScoringClient;

class MlScoringRuleTest {

    private final StubMlScoringClient client = new StubMlScoringClient();
    private final MlScoringRule rule = new MlScoringRule(client, 0.75, 35);

    private Transaction txn() {
        return Transaction.builder()
                .transactionId("t1")
                .customerId("c1")
                .merchantId("m1")
                .amount(12500.0)
                .country("US")
                .deviceId("d1")
                .timestamp(Instant.parse("2026-05-01T12:34:56Z"))
                .build();
    }

    private MlScoreResponse response(double riskScore) {
        return new MlScoreResponse(
                "isolation_forest",
                "iforest-demo-v1",
                riskScore,
                riskScore >= 0.75,
                List.of());
    }

    @Test
    void nameAndWeightComeFromRuleContract() {
        assertThat(rule.name()).isEqualTo("ML_SCORING");
        assertThat(rule.weight()).isEqualTo(35);
    }

    @Test
    void firesAtThreshold() {
        client.response = Optional.of(response(0.75));

        assertThat(rule.evaluate(txn())).isTrue();
    }

    @Test
    void doesNotFireBelowThreshold() {
        client.response = Optional.of(response(0.74));

        assertThat(rule.evaluate(txn())).isFalse();
    }

    @Test
    void sidecarUnavailableFailsOpen() {
        client.response = Optional.empty();

        assertThat(rule.evaluate(txn())).isFalse();
    }

    @Test
    void clientExceptionFailsOpen() {
        client.exception = new RuntimeException("sidecar down");

        assertThat(rule.evaluate(txn())).isFalse();
    }

    @Test
    void nullTransactionDoesNotCallClient() {
        assertThat(rule.evaluate(null)).isFalse();
        assertThat(client.calls).isZero();
    }

    private static class StubMlScoringClient extends MlScoringClient {

        private Optional<MlScoreResponse> response = Optional.empty();
        private RuntimeException exception;
        private int calls;

        StubMlScoringClient() {
            super("http://unused", 1, 1);
        }

        @Override
        public Optional<MlScoreResponse> score(Transaction txn) {
            calls++;
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
