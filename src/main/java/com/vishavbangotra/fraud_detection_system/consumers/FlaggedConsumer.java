package com.vishavbangotra.fraud_detection_system.consumers;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.alerting.WebhookAlertService;
import com.vishavbangotra.fraud_detection_system.config.KafkaConfig;
import com.vishavbangotra.fraud_detection_system.persistence.FlaggedTransactionEntity;
import com.vishavbangotra.fraud_detection_system.persistence.FlaggedTransactionRepository;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.streaming.LiveFeedService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FlaggedConsumer {

    private final FlaggedTransactionRepository repository;
    private final WebhookAlertService alertService;
    private final LiveFeedService liveFeedService;

    public FlaggedConsumer(FlaggedTransactionRepository repository,
                           WebhookAlertService alertService,
                           LiveFeedService liveFeedService) {
        this.repository = repository;
        this.alertService = alertService;
        this.liveFeedService = liveFeedService;
    }

    @KafkaListener(
            topics = KafkaConfig.TRANSACTIONS_FLAGGED,
            groupId = "flagged-writer",
            containerFactory = ScoredTransactionConsumerConfig.SCORED_LISTENER_FACTORY)
    public void onFlagged(ScoredTransaction scored) {
        if (scored == null || scored.getTransaction() == null) {
            log.warn("Skipping null flagged transaction");
            return;
        }
        repository.save(FlaggedTransactionEntity.fromScored(scored));
        alertService.send(scored);
        liveFeedService.broadcastFlagged(scored);
    }
}
