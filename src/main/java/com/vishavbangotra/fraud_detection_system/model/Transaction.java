package com.vishavbangotra.fraud_detection_system.model;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

    @NotBlank
    private String transactionId;

    @NotBlank
    private String customerId;

    @NotBlank
    private String merchantId;

    @NotNull
    @Positive
    private Double amount;

    @NotBlank
    private String country;

    private String city;

    private Double latitude;

    private Double longitude;

    @NotBlank
    private String deviceId;

    private String ipAddress;

    @NotNull
    private Instant timestamp;
}
