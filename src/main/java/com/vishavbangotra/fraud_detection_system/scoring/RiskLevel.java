package com.vishavbangotra.fraud_detection_system.scoring;

public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public static RiskLevel fromScore(int score) {
        if (score >= 70) return HIGH;
        if (score >= 30) return MEDIUM;
        return LOW;
    }
}
