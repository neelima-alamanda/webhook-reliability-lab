package com.webhooklab.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSourceRequest(
    @NotBlank(message = "Name is required")
    String name
) {}
