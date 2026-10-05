package com.webhooklab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record WebhookEventRequest(
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

    String currentStatus
) {}
