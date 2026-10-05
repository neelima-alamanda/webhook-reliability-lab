package com.webhooklab.controller;

import com.webhooklab.dto.DeliveryAttemptResponse;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Webhook Events", description = "Inspect ingested webhook events and delivery attempts for sources owned by the authenticated user (Requires JWT Bearer token)")
public class EventController {

    private final EventService eventService;

    @Operation(summary = "Get webhook events", description = "Retrieves webhook events belonging to sources owned by the authenticated user. Supports optional filtering by sourceId and event status. Requires Bearer JWT.")
    @GetMapping
    public ResponseEntity<List<WebhookEventResponse>> getEvents(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) EventStatus status,
            Authentication authentication
    ) {
        List<WebhookEventResponse> events = eventService.getEvents(sourceId, status, authentication.getName());
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "Get delivery attempts for an event", description = "Retrieves chronological delivery attempt records for a specific webhook event. Verifies the authenticated user owns the event's source. Requires Bearer JWT.")
    @GetMapping("/{id}/attempts")
    public ResponseEntity<List<DeliveryAttemptResponse>> getDeliveryAttempts(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<DeliveryAttemptResponse> attempts = eventService.getDeliveryAttempts(id, authentication.getName());
        return ResponseEntity.ok(attempts);
    }
}
