package com.webhooklab.service;

import com.webhooklab.dto.DeliveryAttemptResponse;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.DeliveryAttempt;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookEvent;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.DeliveryAttemptRepository;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final WebhookEventRepository webhookEventRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookSourceRepository webhookSourceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> getEvents(Long sourceId, EventStatus status, String userIdentifier) {
        User user = findUser(userIdentifier);

        if (sourceId != null) {
            WebhookSource source = webhookSourceRepository.findById(sourceId)
                    .filter(s -> s.getOwner().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Webhook source not found with id: " + sourceId));

            List<WebhookEvent> events;
            if (status != null) {
                events = webhookEventRepository.findBySourceIdAndStatusOrderByIdDesc(source.getId(), status);
            } else {
                events = webhookEventRepository.findBySourceIdOrderByIdDesc(source.getId());
            }

            return events.stream().map(this::toEventResponse).toList();
        }

        List<WebhookEvent> events;
        if (status != null) {
            events = webhookEventRepository.findBySourceOwnerIdAndStatusOrderByIdDesc(user.getId(), status);
        } else {
            events = webhookEventRepository.findBySourceOwnerIdOrderByIdDesc(user.getId());
        }

        return events.stream().map(this::toEventResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> getDeliveryAttempts(Long eventId, String userIdentifier) {
        User user = findUser(userIdentifier);

        WebhookEvent event = webhookEventRepository.findById(eventId)
                .filter(e -> e.getSource().getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook event not found with id: " + eventId));

        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByEventIdOrderByIdAsc(event.getId());

        return attempts.stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    private User findUser(String identifier) {
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
    }

    private WebhookEventResponse toEventResponse(WebhookEvent event) {
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

    private DeliveryAttemptResponse toAttemptResponse(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getEvent().getId(),
                attempt.getReceivedAt(),
                attempt.getOutcome(),
                attempt.getDetails()
        );
    }
}
