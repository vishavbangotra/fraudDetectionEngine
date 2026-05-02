package com.vishavbangotra.fraud_detection_system.scoring.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

@Component
public class AmountThresholdRule implements Rule {

    private final double threshold;

    public AmountThresholdRule(@Value("${fraud.rules.amount.threshold:10000}") double threshold) {
        this.threshold = threshold;
    }

    @Override
    public String name() {
        return "AMOUNT_THRESHOLD";
    }

    @Override
    public int weight() {
        return 40;
    }

    @Override
    public boolean evaluate(Transaction txn) {
        return txn != null && txn.getAmount() != null && txn.getAmount() > threshold;
    }
}
