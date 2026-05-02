package com.vishavbangotra.fraud_detection_system.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService.Stream;

class LiveFeedServiceTest {

    private static ScoredTransaction sample() {
        Transaction txn = Transaction.builder()
                .transactionId("t1")
                .customerId("c1")
                .merchantId("m1")
                .amount(50.0)
                .country("US")
                .deviceId("d1")
                .timestamp(Instant.now())
                .build();
        return ScoredTransaction.builder()
                .transaction(txn)
                .score(10)
                .triggeredRules(List.of())
                .riskLevel(RiskLevel.LOW)
                .build();
    }

    @Test
    void registerAddsEmitterAndSendsReady() {
        LiveFeedService svc = new LiveFeedService();
        svc.register(Stream.SCORED);
        assertThat(svc.scoredEmitterCount()).isEqualTo(1);
        assertThat(svc.flaggedEmitterCount()).isZero();
    }

    @Test
    void broadcastScoredDeliversToAllScoredEmitters() {
        LiveFeedService svc = new LiveFeedService();
        svc.register(Stream.SCORED);
        svc.register(Stream.SCORED);
        svc.register(Stream.FLAGGED);

        svc.broadcastScored(sample());

        // Still registered (no IO failure happens with the in-memory emitter as long as send succeeds).
        assertThat(svc.scoredEmitterCount()).isEqualTo(2);
        assertThat(svc.flaggedEmitterCount()).isEqualTo(1);
    }

    @Test
    void deadEmittersAreRemovedOnBroadcast() {
        LiveFeedService svc = new LiveFeedService();
        var emitter = svc.register(Stream.SCORED);
        // Put the emitter into a completed-with-error state so the next send() throws IllegalStateException.
        emitter.completeWithError(new IOException("client gone"));

        AtomicInteger before = new AtomicInteger(svc.scoredEmitterCount());
        svc.broadcastScored(sample());
        assertThat(svc.scoredEmitterCount()).isLessThanOrEqualTo(before.get());
        assertThat(svc.scoredEmitterCount()).isZero();
    }
}
