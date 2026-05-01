package com.vishavbangotra.fraud_detection_system.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskLevelTest {

    @Test
    void belowThirtyIsLow() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.fromScore(29)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void thirtyToSixtyNineIsMedium() {
        assertThat(RiskLevel.fromScore(30)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RiskLevel.fromScore(69)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void seventyAndAboveIsHigh() {
        assertThat(RiskLevel.fromScore(70)).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.HIGH);
    }
}
