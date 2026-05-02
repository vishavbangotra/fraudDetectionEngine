package com.vishavbangotra.fraud_detection_system.consumers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.persistence.TransactionEventEntity;
import com.vishavbangotra.fraud_detection_system.persistence.TransactionEventRepository;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService;

class AuditConsumerTest {

    private ScoredTransaction sample() {
        return ScoredTransaction.builder()
                .transaction(Transaction.builder()
                        .transactionId("t1").customerId("c1").merchantId("m1")
                        .amount(50.0).country("US").deviceId("d1")
                        .timestamp(Instant.now()).build())
                .score(10).triggeredRules(List.of()).riskLevel(RiskLevel.LOW).build();
    }

    @Test
    void persistsAndBroadcastsScored() {
        TransactionEventRepository repo = mock(TransactionEventRepository.class);
        LiveFeedService feed = mock(LiveFeedService.class);
        AuditConsumer consumer = new AuditConsumer(repo, feed);

        ScoredTransaction scored = sample();
        consumer.onScored(scored);

        verify(repo).save(any(TransactionEventEntity.class));
        verify(feed).broadcastScored(scored);
    }

    @Test
    void skipsNullScored() {
        TransactionEventRepository repo = mock(TransactionEventRepository.class);
        LiveFeedService feed = mock(LiveFeedService.class);
        AuditConsumer consumer = new AuditConsumer(repo, feed);

        consumer.onScored(null);

        verify(repo, never()).save(any());
        verify(feed, never()).broadcastScored(any());
    }
}
