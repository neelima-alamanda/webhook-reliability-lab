package com.webhooklab.service;

import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.DeliveryAttempt;
import com.webhooklab.entity.DeliveryOutcome;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.WebhookEvent;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.DeliveryAttemptRepository;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookSourceRepository webhookSourceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEventResponse createAndDeliverEvent(Long sourceId, WebhookEventRequest request, LocalDateTime receivedAt) {
        WebhookSource source = webhookSourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook source not found with id: " + sourceId));

        WebhookEvent event = new WebhookEvent();
        event.setSource(source);
        event.setEventId(request.eventId());
        event.setResourceId(request.resourceId());
        event.setSequence(request.sequence());
        event.setType(request.type());
        event.setOccurredAt(request.occurredAt());
        event.setReceivedAt(receivedAt);
        event.setStatus(EventStatus.PROCESSING);
        event.setCurrentStatus(request.currentStatus());
        event.setLastSequence(request.sequence());

        // Save and flush to enforce unique constraint uk_source_event immediately
        event = webhookEventRepository.saveAndFlush(event);

        // Mark as DELIVERED after successful processing
        event.setStatus(EventStatus.DELIVERED);
        event = webhookEventRepository.saveAndFlush(event);

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setEvent(event);
        attempt.setReceivedAt(receivedAt);
        attempt.setOutcome(DeliveryOutcome.SUCCESS);
        attempt.setDetails("Webhook event ingested and delivered successfully");
        deliveryAttemptRepository.save(attempt);

        return toResponse(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEventResponse recordDuplicateAttempt(Long sourceId, String eventId, LocalDateTime receivedAt, String details) {
        WebhookEvent event = webhookEventRepository.findBySourceIdAndEventId(sourceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found with eventId: " + eventId));

        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setEvent(event);
        attempt.setReceivedAt(receivedAt);
        attempt.setOutcome(DeliveryOutcome.DUPLICATE);
        attempt.setDetails(details);
        deliveryAttemptRepository.save(attempt);

        return toResponse(event);
    }

    public WebhookEventResponse toResponse(WebhookEvent event) {
        return new WebhookEventResponse(
                event.getId(),
                event.getSource().getId(),
                event.getEventId(),
                event.getResourceId(),
                event.getSequence(),
                event.getType(),
                event.getOccurredAt(),
                event.getReceivedAt(),
                event.getStatus(),
                event.getCurrentStatus(),
                event.getLastSequence()
        );
    }
}
