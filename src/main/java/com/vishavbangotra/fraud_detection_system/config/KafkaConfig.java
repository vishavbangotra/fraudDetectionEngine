package com.vishavbangotra.fraud_detection_system.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TRANSACTIONS_RAW = "transactions.raw";
    public static final String TRANSACTIONS_SCORED = "transactions.scored";
    public static final String TRANSACTIONS_FLAGGED = "transactions.flagged";

    @Bean
    public NewTopic transactionsRawTopic() {
        return TopicBuilder.name(TRANSACTIONS_RAW).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic transactionsScoredTopic() {
        return TopicBuilder.name(TRANSACTIONS_SCORED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic transactionsFlaggedTopic() {
        return TopicBuilder.name(TRANSACTIONS_FLAGGED).partitions(3).replicas(1).build();
    }
}
