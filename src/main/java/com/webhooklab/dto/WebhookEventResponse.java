package com.webhooklab.dto;

import com.webhooklab.entity.EventStatus;

import java.time.LocalDateTime;

public record WebhookEventResponse(
    Long id,
    Long sourceId,
    String eventId,
    String resourceId,
    Long sequence,
    String type,
    LocalDateTime occurredAt,
    LocalDateTime receivedAt,
    EventStatus status,
    String currentStatus,
    Long lastSequence
) {}
