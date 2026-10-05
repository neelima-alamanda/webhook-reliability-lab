package com.webhooklab.dto;

import com.webhooklab.entity.Role;

public record AuthResponse(
    String token,
    String username,
    Role role
) {}
