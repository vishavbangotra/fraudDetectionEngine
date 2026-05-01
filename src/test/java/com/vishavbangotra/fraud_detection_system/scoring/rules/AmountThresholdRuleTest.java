package com.vishavbangotra.fraud_detection_system.scoring.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

class AmountThresholdRuleTest {

    private final AmountThresholdRule rule = new AmountThresholdRule(10000.0);

    private Transaction txn(Double amount) {
        return Transaction.builder().transactionId("t").customerId("c").amount(amount).build();
    }

    @Test
    void firesAboveThreshold() {
        assertThat(rule.evaluate(txn(10001.0))).isTrue();
    }

    @Test
    void doesNotFireAtThreshold() {
        assertThat(rule.evaluate(txn(10000.0))).isFalse();
    }

    @Test
    void doesNotFireBelowThreshold() {
        assertThat(rule.evaluate(txn(500.0))).isFalse();
    }

    @Test
    void doesNotFireOnNullAmount() {
        assertThat(rule.evaluate(txn(null))).isFalse();
    }

    @Test
    void doesNotFireOnNullTxn() {
        assertThat(rule.evaluate(null)).isFalse();
    }
}
