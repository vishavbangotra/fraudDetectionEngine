package com.vishavbangotra.fraud_detection_system.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.rules.Rule;

class RuleEngineTest {

    private Transaction sampleTxn() {
        return Transaction.builder()
                .transactionId("t1")
                .customerId("c1")
                .merchantId("m1")
                .amount(100.0)
                .country("US")
                .deviceId("d1")
                .timestamp(Instant.now())
                .build();
    }

    private Rule rule(String name, int weight, boolean fires) {
        Rule r = mock(Rule.class);
        when(r.name()).thenReturn(name);
        when(r.weight()).thenReturn(weight);
        when(r.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(fires);
        return r;
    }

    @Test
    void noRulesFire_returnsLowZero() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("A", 40, false),
                rule("B", 25, false)));

        ScoredTransaction result = engine.score(sampleTxn());

        assertThat(result.getScore()).isZero();
        assertThat(result.getTriggeredRules()).isEmpty();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void mediumWhenSingleHeavyRuleFires() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("AMOUNT", 40, true),
                rule("VEL", 25, false)));

        ScoredTransaction result = engine.score(sampleTxn());

        assertThat(result.getScore()).isEqualTo(40);
        assertThat(result.getTriggeredRules()).containsExactly("AMOUNT");
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void highWhenMultipleRulesFire() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("AMOUNT", 40, true),
                rule("VEL", 25, true),
                rule("GEO", 25, true)));

        ScoredTransaction result = engine.score(sampleTxn());

        assertThat(result.getScore()).isEqualTo(90);
        assertThat(result.getTriggeredRules()).containsExactly("AMOUNT", "VEL", "GEO");
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void scoreCappedAt100() {
        RuleEngine engine = new RuleEngine(List.of(
                rule("A", 60, true),
                rule("B", 60, true)));

        ScoredTransaction result = engine.score(sampleTxn());

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void rulesThatThrowAreSkipped() {
        Rule throwing = mock(Rule.class);
        when(throwing.name()).thenReturn("BROKEN");
        when(throwing.weight()).thenReturn(40);
        when(throwing.evaluate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("redis down"));

        RuleEngine engine = new RuleEngine(List.of(
                throwing,
                rule("AMOUNT", 40, true)));

        ScoredTransaction result = engine.score(sampleTxn());

        assertThat(result.getTriggeredRules()).containsExactly("AMOUNT");
        assertThat(result.getScore()).isEqualTo(40);
    }
}
