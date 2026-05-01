package com.vishavbangotra.fraud_detection_system.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.config.KafkaConfig;
import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.RuleEngine;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;
import com.vishavbangotra.fraud_detection_system.serdes.ScoredTransactionDeserializer;
import com.vishavbangotra.fraud_detection_system.serdes.TransactionSerializer;

class FraudDetectionStreamTest {

    private TopologyTestDriver driver;
    private TestInputTopic<String, Transaction> rawIn;
    private TestOutputTopic<String, ScoredTransaction> scoredOut;
    private TestOutputTopic<String, ScoredTransaction> flaggedOut;

    @BeforeEach
    void setUp() {
        RuleEngine engine = mock(RuleEngine.class);
        when(engine.score(any(Transaction.class))).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            RiskLevel level = txn.getAmount() > 10000 ? RiskLevel.HIGH : RiskLevel.LOW;
            int score = level == RiskLevel.HIGH ? 80 : 0;
            return ScoredTransaction.builder()
                    .transaction(txn)
                    .score(score)
                    .triggeredRules(level == RiskLevel.HIGH ? List.of("AMOUNT") : List.of())
                    .riskLevel(level)
                    .build();
        });

        StreamsBuilder builder = new StreamsBuilder();
        new FraudDetectionStream(engine).fraudDetectionStreamProcessor(builder);
        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        driver = new TopologyTestDriver(topology, props);

        rawIn = driver.createInputTopic(
                KafkaConfig.TRANSACTIONS_RAW,
                new StringSerializer(),
                new TransactionSerializer());
        scoredOut = driver.createOutputTopic(
                KafkaConfig.TRANSACTIONS_SCORED,
                new StringDeserializer(),
                new ScoredTransactionDeserializer());
        flaggedOut = driver.createOutputTopic(
                KafkaConfig.TRANSACTIONS_FLAGGED,
                new StringDeserializer(),
                new ScoredTransactionDeserializer());
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.close();
    }

    private Transaction txn(String id, double amount) {
        return Transaction.builder()
                .transactionId(id)
                .customerId("cust-1")
                .merchantId("m1")
                .amount(amount)
                .country("US")
                .deviceId("d1")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void lowRiskTxnGoesToScoredButNotFlagged() {
        rawIn.pipeInput("cust-1", txn("t1", 50.0));

        List<ScoredTransaction> scored = scoredOut.readValuesToList();
        assertThat(scored).hasSize(1);
        assertThat(scored.get(0).getRiskLevel()).isEqualTo(RiskLevel.LOW);

        assertThat(flaggedOut.isEmpty()).isTrue();
    }

    @Test
    void highRiskTxnGoesToBothScoredAndFlagged() {
        rawIn.pipeInput("cust-1", txn("t1", 25000.0));

        List<ScoredTransaction> scored = scoredOut.readValuesToList();
        List<ScoredTransaction> flagged = flaggedOut.readValuesToList();

        assertThat(scored).hasSize(1);
        assertThat(scored.get(0).getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).getTransaction().getTransactionId()).isEqualTo("t1");
    }

    @Test
    void mixedBatch_onlyHighRiskFlagged() {
        rawIn.pipeInput("cust-1", txn("low", 100.0));
        rawIn.pipeInput("cust-1", txn("high", 50000.0));
        rawIn.pipeInput("cust-1", txn("low2", 200.0));

        assertThat(scoredOut.readValuesToList()).hasSize(3);
        List<ScoredTransaction> flagged = flaggedOut.readValuesToList();
        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).getTransaction().getTransactionId()).isEqualTo("high");
    }
}
