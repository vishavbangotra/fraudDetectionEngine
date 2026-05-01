package com.vishavbangotra.fraud_detection_system.scoring;

import java.util.List;

import com.vishavbangotra.fraud_detection_system.model.Transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ScoredTransaction {
    private Transaction transaction;
    private int score;
    private List<String> triggeredRules;
    private RiskLevel riskLevel;
}
