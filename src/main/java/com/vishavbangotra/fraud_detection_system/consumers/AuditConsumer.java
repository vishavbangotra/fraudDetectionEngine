package com.vishavbangotra.fraud_detection_system.consumers;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.config.KafkaConfig;
import com.vishavbangotra.fraud_detection_system.persistence.TransactionEventEntity;
import com.vishavbangotra.fraud_detection_system.persistence.TransactionEventRepository;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuditConsumer {

    private final TransactionEventRepository repository;

    public AuditConsumer(TransactionEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = KafkaConfig.TRANSACTIONS_SCORED,
            groupId = "audit-writer",
            containerFactory = ScoredTransactionConsumerConfig.SCORED_LISTENER_FACTORY)
    public void onScored(ScoredTransaction scored) {
        if (scored == null || scored.getTransaction() == null) {
            log.warn("Skipping null scored transaction");
            return;
        }
        repository.save(TransactionEventEntity.fromScored(scored));
    }
}
