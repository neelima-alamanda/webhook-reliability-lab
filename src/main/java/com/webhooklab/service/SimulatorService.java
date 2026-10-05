package com.webhooklab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.SimulatorRequest;
import com.webhooklab.dto.SimulatorResponse;
import com.webhooklab.dto.SimulatorScenario;
import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import com.webhooklab.security.HmacService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SimulatorService {

    private final WebhookSourceRepository webhookSourceRepository;
    private final UserRepository userRepository;
    private final WebhookIngestionService webhookIngestionService;
    private final HmacService hmacService;
    private final ObjectMapper objectMapper;

    @Value("${webhook.late-threshold-seconds:300}")
    private long lateThresholdSeconds = 300;

    public SimulatorResponse simulate(SimulatorRequest request, String userIdentifier) {
        User user = findUser(userIdentifier);

        WebhookSource source = webhookSourceRepository.findById(request.sourceId())
                .filter(s -> s.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Webhook source not found with id: " + request.sourceId()));

        SimulatorScenario scenario = SimulatorScenario.fromString(request.scenario());

        return switch (scenario) {
            case NORMAL -> simulateNormal(request, source);
            case DUPLICATE -> simulateDuplicate(request, source);
            case CONCURRENT_DUPLICATE -> simulateConcurrentDuplicate(request, source);
            case OUT_OF_ORDER -> simulateOutOfOrder(request, source);
            case LATE -> simulateLate(request, source);
            case INVALID_SIGNATURE -> simulateInvalidSignature(request, source);
        };
    }

    private SimulatorResponse simulateNormal(SimulatorRequest request, WebhookSource source) {
        byte[] payload = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                request.occurredAt(),
                request.currentStatus()
        );
        String signature = hmacService.calculateHmacSha256(payload, source.getSecret());
        WebhookEventResponse response = webhookIngestionService.ingestWebhook(source.getId(), signature, payload);

        return new SimulatorResponse(
                SimulatorScenario.NORMAL.name(),
                "SUCCESS",
                "Simulated normal webhook event delivery",
                List.of(response)
        );
    }

    private SimulatorResponse simulateDuplicate(SimulatorRequest request, WebhookSource source) {
        byte[] payload = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                request.occurredAt(),
                request.currentStatus()
        );
        String signature = hmacService.calculateHmacSha256(payload, source.getSecret());

        WebhookEventResponse response1 = webhookIngestionService.ingestWebhook(source.getId(), signature, payload);
        WebhookEventResponse response2 = webhookIngestionService.ingestWebhook(source.getId(), signature, payload);

        return new SimulatorResponse(
                SimulatorScenario.DUPLICATE.name(),
                "SUCCESS",
                "Simulated duplicate webhook event delivery (sent identical event twice)",
                List.of(response1, response2)
        );
    }

    private SimulatorResponse simulateConcurrentDuplicate(SimulatorRequest request, WebhookSource source) {
        byte[] payload = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                request.occurredAt(),
                request.currentStatus()
        );
        String signature = hmacService.calculateHmacSha256(payload, source.getSecret());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<WebhookEventResponse> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    WebhookEventResponse resp = webhookIngestionService.ingestWebhook(source.getId(), signature, payload);
                    responses.add(resp);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        try {
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Concurrent duplicate simulation was interrupted", e);
        } finally {
            executor.shutdown();
        }

        if (responses.isEmpty() && !exceptions.isEmpty()) {
            throw new RuntimeException("Concurrent duplicate simulation failed", exceptions.get(0));
        }

        return new SimulatorResponse(
                SimulatorScenario.CONCURRENT_DUPLICATE.name(),
                "SUCCESS",
                "Simulated concurrent duplicate webhook event delivery simultaneously",
                responses
        );
    }

    private SimulatorResponse simulateOutOfOrder(SimulatorRequest request, WebhookSource source) {
        long nextSequence = request.sequence() + 1;
        String nextEventId = request.eventId() + "_seq" + nextSequence;

        // Event 1 with sequence N+1
        byte[] payload1 = createPayload(
                nextEventId,
                request.resourceId(),
                nextSequence,
                request.type(),
                request.occurredAt().plusSeconds(10),
                request.currentStatus()
        );
        String signature1 = hmacService.calculateHmacSha256(payload1, source.getSecret());
        WebhookEventResponse response1 = webhookIngestionService.ingestWebhook(source.getId(), signature1, payload1);

        // Event 2 with sequence N
        byte[] payload2 = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                request.occurredAt(),
                request.currentStatus()
        );
        String signature2 = hmacService.calculateHmacSha256(payload2, source.getSecret());
        WebhookEventResponse response2 = webhookIngestionService.ingestWebhook(source.getId(), signature2, payload2);

        return new SimulatorResponse(
                SimulatorScenario.OUT_OF_ORDER.name(),
                "SUCCESS",
                "Simulated out-of-order webhook sequence (sequence " + nextSequence + " sent before sequence " + request.sequence() + ")",
                List.of(response1, response2)
        );
    }

    private SimulatorResponse simulateLate(SimulatorRequest request, WebhookSource source) {
        LocalDateTime lateOccurredAt = LocalDateTime.now().minusSeconds(lateThresholdSeconds + 60);

        byte[] payload = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                lateOccurredAt,
                request.currentStatus()
        );
        String signature = hmacService.calculateHmacSha256(payload, source.getSecret());
        WebhookEventResponse response = webhookIngestionService.ingestWebhook(source.getId(), signature, payload);

        return new SimulatorResponse(
                SimulatorScenario.LATE.name(),
                "SUCCESS",
                "Simulated late webhook event delivery (occurredAt is older than late threshold of " + lateThresholdSeconds + " seconds)",
                List.of(response)
        );
    }

    private SimulatorResponse simulateInvalidSignature(SimulatorRequest request, WebhookSource source) {
        byte[] payload = createPayload(
                request.eventId(),
                request.resourceId(),
                request.sequence(),
                request.type(),
                request.occurredAt(),
                request.currentStatus()
        );
        String invalidSignature = "invalid_hmac_signature_value";

        try {
            WebhookEventResponse response = webhookIngestionService.ingestWebhook(source.getId(), invalidSignature, payload);
            return new SimulatorResponse(
                    SimulatorScenario.INVALID_SIGNATURE.name(),
                    "UNEXPECTED",
                    "Expected invalid HMAC signature rejection, but ingestion succeeded",
                    List.of(response)
            );
        } catch (InvalidSignatureException e) {
            return new SimulatorResponse(
                    SimulatorScenario.INVALID_SIGNATURE.name(),
                    "REJECTED",
                    "Simulated invalid HMAC signature: webhook was correctly rejected with 401 Unauthorized (" + e.getMessage() + ")",
                    List.of()
            );
        }
    }

    private byte[] createPayload(String eventId, String resourceId, Long sequence, String type, LocalDateTime occurredAt, String currentStatus) {
        try {
            WebhookEventRequest eventRequest = new WebhookEventRequest(
                    eventId,
                    resourceId,
                    sequence,
                    type,
                    occurredAt,
                    currentStatus
            );
            return objectMapper.writeValueAsBytes(eventRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize simulated webhook event request", e);
        }
    }

    private User findUser(String identifier) {
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
    }
}
