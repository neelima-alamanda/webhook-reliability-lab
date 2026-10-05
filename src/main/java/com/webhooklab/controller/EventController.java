package com.webhooklab.controller;

import com.webhooklab.dto.DeliveryAttemptResponse;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.service.EventService;
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
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<WebhookEventResponse>> getEvents(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) EventStatus status,
            Authentication authentication
    ) {
        List<WebhookEventResponse> events = eventService.getEvents(sourceId, status, authentication.getName());
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<List<DeliveryAttemptResponse>> getDeliveryAttempts(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<DeliveryAttemptResponse> attempts = eventService.getDeliveryAttempts(id, authentication.getName());
        return ResponseEntity.ok(attempts);
    }
}
