package com.webhooklab.controller;

import com.webhooklab.dto.CreateSourceRequest;
import com.webhooklab.dto.SourceResponse;
import com.webhooklab.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
@Tag(name = "Webhook Sources", description = "Manage webhook sources owned by the authenticated user (Requires JWT Bearer token)")
public class SourceController {

    private final SourceService sourceService;

    @Operation(summary = "Create a webhook source", description = "Creates a new webhook source owned by the authenticated user and generates a cryptographically secure random secret. Requires Bearer JWT.")
    @PostMapping
    public ResponseEntity<SourceResponse> createSource(
            @Valid @RequestBody CreateSourceRequest request,
            Authentication authentication
    ) {
        SourceResponse response = sourceService.createSource(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get user's webhook sources", description = "Retrieves all webhook sources owned by the authenticated user without exposing secrets. Requires Bearer JWT.")
    @GetMapping
    public ResponseEntity<List<SourceResponse>> getSources(Authentication authentication) {
        List<SourceResponse> response = sourceService.getSources(authentication.getName());
        return ResponseEntity.ok(response);
    }
}
