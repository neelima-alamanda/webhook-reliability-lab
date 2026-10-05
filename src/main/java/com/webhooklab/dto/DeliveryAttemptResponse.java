package com.webhooklab.dto;

import com.webhooklab.entity.DeliveryOutcome;

import java.time.LocalDateTime;

public record DeliveryAttemptResponse(
    Long id,
    Long eventId,
    LocalDateTime receivedAt,
    DeliveryOutcome outcome,
    String details
) {}
