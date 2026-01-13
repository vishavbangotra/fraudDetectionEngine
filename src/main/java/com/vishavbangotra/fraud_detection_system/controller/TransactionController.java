package com.vishavbangotra.fraud_detection_system.controller;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishavbangotra.fraud_detection_system.model.Transaction;

@RestController("/api/transactions")
public class TransactionController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/send")
    public String sendTransaction() throws Exception {
        for (int i = 0; i < 10; i++) {
            String transactionId = "txn-" + System.currentTimeMillis() + "-" + i;
            double amount = 8000 + new Random().nextDouble() * (11000 - 8000);

            Transaction txn = new Transaction(transactionId, amount, LocalDateTime.now().toString());

            String txnJson = new ObjectMapper().writeValueAsString(txn);

            kafkaTemplate.send("transactions", transactionId, txnJson).get();

        }
        return "Transaction sent successfully";
    }

}
