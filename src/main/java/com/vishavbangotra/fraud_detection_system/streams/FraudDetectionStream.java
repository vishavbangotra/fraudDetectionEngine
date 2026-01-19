package com.vishavbangotra.fraud_detection_system.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.serdes.TransactionSerde;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableKafkaStreams
@Slf4j
public class FraudDetectionStream {

    private static final String TRANSACTIONS_TOPIC = "transactions";
    private static final String SUSPICIOUS_TRANSACTIONS_TOPIC = "suspicious-transactions";
    private static final double FRAUD_THRESHOLD = 10000.0;

    /**
     * Processes the transactions topic and filters suspicious transactions.
     * Suspicious transactions are those with amounts exceeding the fraud threshold.
     *
     * @param builder the StreamsBuilder to create the stream
     * @return a KStream of transactions
     */
    @Bean
    public KStream<String, Transaction> fraudDetectionStreamProcessor(StreamsBuilder builder) {

        KStream<String, Transaction> transactionStream = builder.stream(TRANSACTIONS_TOPIC,
                Consumed.with(Serdes.String(), new TransactionSerde()));

        transactionStream
                .filter((key, value) -> isSuspicious(value))
                .peek((key, value) -> log.warn("⚠️ Fraud Alert - transactionId: {} value: {}", key, value))
                .to(SUSPICIOUS_TRANSACTIONS_TOPIC);
        return transactionStream;
    }

    private boolean isSuspicious(Transaction transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return false;
        }
        return transaction.getAmount() > FRAUD_THRESHOLD;
    }
}
