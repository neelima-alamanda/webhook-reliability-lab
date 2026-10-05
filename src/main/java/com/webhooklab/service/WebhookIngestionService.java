package com.webhooklab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.InvalidPayloadException;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import com.webhooklab.security.HmacService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WebhookIngestionService {

    private final WebhookSourceRepository webhookSourceRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventService webhookEventService;
    private final HmacService hmacService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WebhookEventResponse ingestWebhook(Long sourceId, String signature, byte[] rawBody) {
        // 1. Resolve WebhookSource by sourceId
        WebhookSource source = webhookSourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook source not found with id: " + sourceId));

        // 2 & 3. Verify HMAC-SHA256 signature
        if (signature == null || signature.isBlank()) {
            throw new InvalidSignatureException("Missing X-Webhook-Signature header");
        }

        byte[] payload = rawBody != null ? rawBody : new byte[0];
        if (!hmacService.verifySignature(payload, source.getSecret(), signature)) {
            throw new InvalidSignatureException("Invalid HMAC signature");
        }

        // 4. Deserialize and validate payload only after successful signature verification
        if (payload.length == 0) {
            throw new InvalidPayloadException("Request body cannot be empty");
        }

        WebhookEventRequest request;
        try {
            request = objectMapper.readValue(payload, WebhookEventRequest.class);
            if (request == null) {
                throw new InvalidPayloadException("Request body cannot be empty");
            }
        } catch (Exception e) {
            throw new InvalidPayloadException("Invalid or malformed JSON payload");
        }

        Set<ConstraintViolation<WebhookEventRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> fieldErrors = new HashMap<>();
            for (ConstraintViolation<WebhookEventRequest> violation : violations) {
                fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
            }
            throw new InvalidPayloadException("Validation failed", fieldErrors);
        }

        LocalDateTime receivedAt = LocalDateTime.now();

        // 5 & 6. Duplicate handling with concurrency race safety
        boolean exists = webhookEventRepository.existsBySourceIdAndEventId(source.getId(), request.eventId());
        if (exists) {
            return webhookEventService.recordDuplicateAttempt(
                    source.getId(),
                    request.eventId(),
                    receivedAt,
                    "Duplicate delivery: event with id '" + request.eventId() + "' has already been processed"
            );
        }

        try {
            return webhookEventService.createAndDeliverEvent(source.getId(), request, receivedAt);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request created the event in the race window
            return webhookEventService.recordDuplicateAttempt(
                    source.getId(),
                    request.eventId(),
                    receivedAt,
                    "Duplicate delivery (concurrent race): event with id '" + request.eventId() + "' was concurrently processed"
            );
        }
    }
}
