package com.vishavbangotra.fraud_detection_system.scoring.ml;

import java.util.List;

public record MlScoreResponse(
        String model,
        String modelVersion,
        double riskScore,
        boolean anomaly,
        List<String> reasonCodes) {

    public boolean isValid() {
        return !Double.isNaN(riskScore)
                && !Double.isInfinite(riskScore)
                && riskScore >= 0.0
                && riskScore <= 1.0;
    }
}
