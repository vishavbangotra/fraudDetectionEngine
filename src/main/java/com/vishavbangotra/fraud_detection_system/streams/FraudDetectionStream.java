package com.vishavbangotra.fraud_detection_system.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import com.vishavbangotra.fraud_detection_system.config.KafkaConfig;
import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.RuleEngine;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.serdes.ScoredTransactionSerde;
import com.vishavbangotra.fraud_detection_system.serdes.TransactionSerde;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableKafkaStreams
@Slf4j
public class FraudDetectionStream {

    private final RuleEngine ruleEngine;

    public FraudDetectionStream(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Bean
    public KStream<String, ScoredTransaction> fraudDetectionStreamProcessor(StreamsBuilder builder) {
        ScoredTransactionSerde scoredSerde = new ScoredTransactionSerde();

        KStream<String, Transaction> raw = builder.stream(
                KafkaConfig.TRANSACTIONS_RAW,
                Consumed.with(Serdes.String(), new TransactionSerde()));

        KStream<String, ScoredTransaction> scored = raw.mapValues(ruleEngine::score);

        scored.to(KafkaConfig.TRANSACTIONS_SCORED,
                Produced.with(Serdes.String(), scoredSerde));

        scored.filter((key, value) -> value != null && value.getRiskLevel() == RiskLevel.HIGH)
                .peek((key, value) -> log.warn("Fraud HIGH - txn {} score {} rules {}",
                        value.getTransaction().getTransactionId(),
                        value.getScore(),
                        value.getTriggeredRules()))
                .to(KafkaConfig.TRANSACTIONS_FLAGGED,
                        Produced.with(Serdes.String(), scoredSerde));

        return scored;
    }
}
