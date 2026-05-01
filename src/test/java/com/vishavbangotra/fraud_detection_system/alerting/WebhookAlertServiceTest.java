package com.vishavbangotra.fraud_detection_system.alerting;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

class WebhookAlertServiceTest {

    private ScoredTransaction sampleScored() {
        return ScoredTransaction.builder()
                .transaction(Transaction.builder()
                        .transactionId("t1")
                        .customerId("c1")
                        .amount(20000.0)
                        .timestamp(Instant.now())
                        .build())
                .score(80)
                .triggeredRules(List.of("AMOUNT_THRESHOLD"))
                .riskLevel(RiskLevel.HIGH)
                .build();
    }

    @Test
    void blankUrlIsNoOp() {
        WebhookAlertService svc = new WebhookAlertService("");
        assertThatCode(() -> svc.send(sampleScored())).doesNotThrowAnyException();
    }

    @Test
    void nullUrlIsNoOp() {
        WebhookAlertService svc = new WebhookAlertService(null);
        assertThatCode(() -> svc.send(sampleScored())).doesNotThrowAnyException();
    }

    @Test
    void unreachableUrlExhaustsRetriesWithoutThrowing() {
        WebhookAlertService svc = new WebhookAlertService("http://127.0.0.1:1");
        assertThatCode(() -> svc.send(sampleScored())).doesNotThrowAnyException();
    }
}
