package com.vishavbangotra.fraud_detection_system.controller;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private KafkaTemplate<String, Transaction> kafkaTemplate;

    @PostMapping("/send")
    public String sendTransaction() throws Exception {
        try {

            for (int i = 0; i < 10; i++) {
                String transactionId = "txn-" + System.currentTimeMillis() + "-" + i;
                double amount = 8000 + new Random().nextDouble() * 3000;

                Transaction txn = new Transaction(transactionId, amount, LocalDateTime.now().toString());

                kafkaTemplate.send("transactions", transactionId, txn);
            }
            return "✅ Transactions sent successfully";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

}
