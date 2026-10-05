package com.webhooklab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record SimulatorRequest(
    @NotNull(message = "Source ID is required")
    @Positive(message = "Source ID must be positive")
    Long sourceId,

    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotBlank(message = "Resource ID is required")
    String resourceId,

    @NotNull(message = "Sequence is required")
    @Positive(message = "Sequence must be positive")
    Long sequence,

    @NotBlank(message = "Type is required")
    String type,

    @NotNull(message = "Occurred timestamp is required")
    LocalDateTime occurredAt,

    String currentStatus,

    @NotBlank(message = "Scenario is required")
    String scenario
) {}
