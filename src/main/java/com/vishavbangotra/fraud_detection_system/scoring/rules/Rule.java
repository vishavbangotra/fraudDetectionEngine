package com.vishavbangotra.fraud_detection_system.scoring.rules;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

public interface Rule {

    String name();

    int weight();

    boolean evaluate(Transaction txn);
}
