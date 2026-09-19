package com.schemabridge.domain.enums;

public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    public static ConfidenceLevel fromScore(double score, double highThreshold, double mediumThreshold) {
        if (score >= highThreshold) {
            return HIGH;
        } else if (score >= mediumThreshold) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
}
