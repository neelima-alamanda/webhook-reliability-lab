package com.webhooklab.dto;

import java.time.LocalDateTime;

public record SourceResponse(
    Long id,
    String name,
    LocalDateTime createdAt
) {}
