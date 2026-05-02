package com.vishavbangotra.fraud_detection_system.consumers;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.serdes.ScoredTransactionDeserializer;

@Configuration
public class ScoredTransactionConsumerConfig {

    public static final String SCORED_LISTENER_FACTORY = "scoredTxnListenerContainerFactory";

    private final String bootstrapServers;

    public ScoredTransactionConsumerConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ConsumerFactory<String, ScoredTransaction> scoredTransactionConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ScoredTransactionDeserializer());
    }

    @Bean(name = SCORED_LISTENER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, ScoredTransaction> scoredTxnListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ScoredTransaction> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(scoredTransactionConsumerFactory());
        return factory;
    }
}
