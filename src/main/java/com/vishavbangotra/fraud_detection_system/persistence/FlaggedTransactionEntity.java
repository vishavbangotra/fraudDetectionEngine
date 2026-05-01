package com.vishavbangotra.fraud_detection_system.persistence;

import java.time.Instant;
import java.util.List;

import com.vishavbangotra.fraud_detection_system.model.Transaction;
import com.vishavbangotra.fraud_detection_system.scoring.RiskLevel;
import com.vishavbangotra.fraud_detection_system.scoring.ScoredTransaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flagged_transactions", indexes = {
        @Index(name = "idx_flagged_txn_id", columnList = "transactionId"),
        @Index(name = "idx_flagged_customer_id", columnList = "customerId")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FlaggedTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String customerId;

    private String merchantId;

    @Column(nullable = false)
    private Double amount;

    private String country;
    private String city;
    private Double latitude;
    private Double longitude;
    private String deviceId;
    private String ipAddress;

    @Column(nullable = false)
    private Instant transactionTimestamp;

    @Column(nullable = false)
    private int score;

    @Column(length = 512)
    private String triggeredRules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Instant createdAt;

    public static FlaggedTransactionEntity fromScored(ScoredTransaction scored) {
        Transaction t = scored.getTransaction();
        List<String> rules = scored.getTriggeredRules();
        return FlaggedTransactionEntity.builder()
                .transactionId(t.getTransactionId())
                .customerId(t.getCustomerId())
                .merchantId(t.getMerchantId())
                .amount(t.getAmount())
                .country(t.getCountry())
                .city(t.getCity())
                .latitude(t.getLatitude())
                .longitude(t.getLongitude())
                .deviceId(t.getDeviceId())
                .ipAddress(t.getIpAddress())
                .transactionTimestamp(t.getTimestamp())
                .score(scored.getScore())
                .triggeredRules(rules == null ? "" : String.join(",", rules))
                .riskLevel(scored.getRiskLevel())
                .createdAt(Instant.now())
                .build();
    }
}
