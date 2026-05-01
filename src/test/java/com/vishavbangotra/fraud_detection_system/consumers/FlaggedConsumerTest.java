package com.vishavbangotra.fraud_detection_system.consumers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.alerting.WebhookAlertService;
import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.persistence.FlaggedTransactionEntity;
import com.vishavbangotra.fraud_detection_system.persistence.FlaggedTransactionRepository;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService;

class FlaggedConsumerTest {

    private ScoredTransaction sample() {
        return ScoredTransaction.builder()
                .transaction(Transaction.builder()
                        .transactionId("t1").customerId("c1").merchantId("m1")
                        .amount(20000.0).country("US").deviceId("d1")
                        .timestamp(Instant.now()).build())
                .score(85).triggeredRules(List.of("AmountThresholdRule")).riskLevel(RiskLevel.HIGH).build();
    }

    @Test
    void persistsAlertsAndBroadcasts() {
        FlaggedTransactionRepository repo = mock(FlaggedTransactionRepository.class);
        WebhookAlertService alerts = mock(WebhookAlertService.class);
        LiveFeedService feed = mock(LiveFeedService.class);
        FlaggedConsumer consumer = new FlaggedConsumer(repo, alerts, feed);

        ScoredTransaction scored = sample();
        consumer.onFlagged(scored);

        verify(repo).save(any(FlaggedTransactionEntity.class));
        verify(alerts).send(scored);
        verify(feed).broadcastFlagged(scored);
    }

    @Test
    void skipsNullFlagged() {
        FlaggedTransactionRepository repo = mock(FlaggedTransactionRepository.class);
        WebhookAlertService alerts = mock(WebhookAlertService.class);
        LiveFeedService feed = mock(LiveFeedService.class);
        FlaggedConsumer consumer = new FlaggedConsumer(repo, alerts, feed);

        consumer.onFlagged(null);

        verify(repo, never()).save(any());
        verify(alerts, never()).send(any());
        verify(feed, never()).broadcastFlagged(any());
    }
}
