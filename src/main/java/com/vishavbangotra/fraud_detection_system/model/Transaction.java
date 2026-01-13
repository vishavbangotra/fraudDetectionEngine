package com.vishavbangotra.fraud_detection_system.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Transaction {
    private String transactionId;
    private Double amount;
    private String timestamp;
}
