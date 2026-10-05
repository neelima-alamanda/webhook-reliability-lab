package com.webhooklab.dto;

public enum SimulatorScenario {
    NORMAL,
    DUPLICATE,
    CONCURRENT_DUPLICATE,
    OUT_OF_ORDER,
    LATE,
    INVALID_SIGNATURE;

    public static SimulatorScenario fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Scenario cannot be null or blank");
        }
        try {
            return SimulatorScenario.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown simulator scenario: " + value +
                    ". Supported scenarios: NORMAL, DUPLICATE, CONCURRENT_DUPLICATE, OUT_OF_ORDER, LATE, INVALID_SIGNATURE");
        }
    }
}
