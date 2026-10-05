package com.webhooklab.dto;

import java.util.List;

public record SimulatorResponse(
    String scenario,
    String status,
    String message,
    List<WebhookEventResponse> events
) {}
