package com.vishavbangotra.fraud_detection_system.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vishavbangotra.fraud_detection_system.config.KafkaConfig;
import com.vishavbangotra.fraud_detection_system.model.Transaction;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/transactions")
@Slf4j
public class TransactionController {

    private static final List<String> CUSTOMERS = List.of("cust-1", "cust-2", "cust-3", "cust-4", "cust-5");
    private static final List<String> MERCHANTS = List.of("merch-amazon", "merch-walmart", "merch-uber", "merch-starbucks");
    private static final List<String> COUNTRIES = List.of("US", "GB", "DE", "IN", "JP");
    private static final List<String> CITIES = List.of("New York", "London", "Berlin", "Mumbai", "Tokyo");
    private static final List<String> DEVICES = List.of("dev-iphone-1", "dev-android-2", "dev-mac-3", "dev-windows-4");

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    public TransactionController(KafkaTemplate<String, Transaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<String> ingest(@Valid @RequestBody Transaction txn) {
        kafkaTemplate.send(KafkaConfig.TRANSACTIONS_RAW, txn.getCustomerId(), txn);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(txn.getTransactionId());
    }

    @PostMapping("/simulate")
    public ResponseEntity<String> simulate(@RequestParam(defaultValue = "10") int count) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            int countryIdx = rnd.nextInt(COUNTRIES.size());
            Transaction txn = Transaction.builder()
                    .transactionId("txn-" + UUID.randomUUID())
                    .customerId(CUSTOMERS.get(rnd.nextInt(CUSTOMERS.size())))
                    .merchantId(MERCHANTS.get(rnd.nextInt(MERCHANTS.size())))
                    .amount(rnd.nextDouble(10, 15000))
                    .country(COUNTRIES.get(countryIdx))
                    .city(CITIES.get(countryIdx))
                    .latitude(rnd.nextDouble(-60, 60))
                    .longitude(rnd.nextDouble(-180, 180))
                    .deviceId(DEVICES.get(rnd.nextInt(DEVICES.size())))
                    .ipAddress("192.168." + rnd.nextInt(256) + "." + rnd.nextInt(256))
                    .timestamp(Instant.now())
                    .build();
            kafkaTemplate.send(KafkaConfig.TRANSACTIONS_RAW, txn.getCustomerId(), txn);
        }
        log.info("Simulated {} transactions", count);
        return ResponseEntity.accepted().body("Sent " + count + " transactions");
    }

    @PostMapping("/send")
    @Deprecated
    public ResponseEntity<String> sendLegacy() {
        return simulate(10);
    }
}
