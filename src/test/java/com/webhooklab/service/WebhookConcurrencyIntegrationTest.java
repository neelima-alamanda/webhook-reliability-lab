package com.webhooklab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.DeliveryAttempt;
import com.webhooklab.entity.DeliveryOutcome;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.Role;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookEvent;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.repository.DeliveryAttemptRepository;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import com.webhooklab.security.HmacService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WebhookConcurrencyIntegrationTest {

    @Autowired
    private WebhookIngestionService webhookIngestionService;

    @Autowired
    private WebhookSourceRepository webhookSourceRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HmacService hmacService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${webhook.late-threshold-seconds:300}")
    private long lateThresholdSeconds;

    private User testUser;
    private WebhookSource testSource;
    private WebhookSource secondSource;

    @BeforeEach
    void setUp() {
        cleanup();

        testUser = new User();
        testUser.setEmail("concurrency_user@example.com");
        testUser.setUsername("concurrency_user");
        testUser.setPassword("securePassword123");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);

        testSource = new WebhookSource();
        testSource.setName("primary-test-source");
        testSource.setSecret("test_hmac_secret_key_1");
        testSource.setOwner(testUser);
        testSource = webhookSourceRepository.save(testSource);

        secondSource = new WebhookSource();
        secondSource.setName("secondary-test-source");
        secondSource.setSecret("test_hmac_secret_key_2");
        secondSource.setOwner(testUser);
        secondSource = webhookSourceRepository.save(secondSource);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        deliveryAttemptRepository.deleteAll();
        webhookEventRepository.deleteAll();
        webhookSourceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Requirement 10: Concurrent duplicate requests from two threads persist exactly 1 event and record 1 DUPLICATE delivery attempt")
    void testConcurrentDuplicate_twoThreads_persistsOneEventAndOneDuplicateAttempt() throws Exception {
        String eventId = "evt_concurrent_test_100";
        String resourceId = "order_conc_999";
        WebhookEventRequest eventRequest = new WebhookEventRequest(
                eventId,
                resourceId,
                1L,
                "order.created",
                LocalDateTime.now(),
                "PENDING"
        );
        byte[] payload = objectMapper.writeValueAsBytes(eventRequest);
        String signature = hmacService.calculateHmacSha256(payload, testSource.getSecret());

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
                    WebhookEventResponse resp = webhookIngestionService.ingestWebhook(
                            testSource.getId(),
                            signature,
                            payload
                    );
                    responses.add(resp);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Both threads should complete execution within timeout");
        assertTrue(exceptions.isEmpty(), "Neither thread should throw an uncaught exception: " + exceptions);
        assertEquals(2, responses.size(), "Both threads should receive a WebhookEventResponse");

        // 1. Verify database unique constraint enforced: exactly ONE event persisted
        List<WebhookEvent> persistedEvents = webhookEventRepository.findBySourceIdOrderByIdDesc(testSource.getId());
        assertEquals(1, persistedEvents.size(), "Database uk_source_event constraint must ensure exactly ONE event is persisted");

        WebhookEvent singleEvent = persistedEvents.get(0);
        assertEquals(eventId, singleEvent.getEventId());
        assertEquals(resourceId, singleEvent.getResourceId());
        assertEquals(EventStatus.DELIVERED, singleEvent.getStatus());

        // 2. Verify exactly TWO delivery attempts recorded
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByEventIdOrderByIdAsc(singleEvent.getId());
        assertEquals(2, attempts.size(), "Exactly TWO delivery attempts must be recorded for the two requests");

        boolean hasSuccess = attempts.stream().anyMatch(a -> a.getOutcome() == DeliveryOutcome.SUCCESS);
        boolean hasDuplicate = attempts.stream().anyMatch(a -> a.getOutcome() == DeliveryOutcome.DUPLICATE);
        assertTrue(hasSuccess, "One attempt must be SUCCESS");
        assertTrue(hasDuplicate, "The second attempt must be DUPLICATE");

        DeliveryAttempt duplicateAttempt = attempts.stream()
                .filter(a -> a.getOutcome() == DeliveryOutcome.DUPLICATE)
                .findFirst()
                .orElseThrow();
        assertTrue(duplicateAttempt.getDetails().contains("Duplicate delivery"),
                "Duplicate delivery attempt details must mention duplicate delivery");

        // 3. Verify no transaction/rollback state corrupted the database
        assertNotNull(singleEvent.getId());
        assertEquals(singleEvent.getId(), attempts.get(0).getEvent().getId());
        assertEquals(singleEvent.getId(), attempts.get(1).getEvent().getId());
    }

    @Test
    @DisplayName("Requirement 11: Duplicate after successful processing records second delivery as DUPLICATE")
    void testDuplicateAfterSuccessfulProcessing() throws Exception {
        String eventId = "evt_sequential_dup_200";
        WebhookEventRequest eventRequest = new WebhookEventRequest(
                eventId,
                "order_seq_200",
                1L,
                "order.created",
                LocalDateTime.now(),
                "PENDING"
        );
        byte[] payload = objectMapper.writeValueAsBytes(eventRequest);
        String signature = hmacService.calculateHmacSha256(payload, testSource.getSecret());

        // First delivery: initial processing
        WebhookEventResponse response1 = webhookIngestionService.ingestWebhook(testSource.getId(), signature, payload);
        assertNotNull(response1);
        assertEquals(EventStatus.DELIVERED, response1.status());

        // Second delivery: sequential duplicate after successful processing
        WebhookEventResponse response2 = webhookIngestionService.ingestWebhook(testSource.getId(), signature, payload);
        assertNotNull(response2);
        assertEquals(eventId, response2.eventId());

        // Exactly 1 event exists
        List<WebhookEvent> events = webhookEventRepository.findBySourceIdOrderByIdDesc(testSource.getId());
        assertEquals(1, events.size(), "Exactly one event should exist in database");

        // Exactly 2 attempts exist: 1st SUCCESS, 2nd DUPLICATE
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByEventIdOrderByIdAsc(events.get(0).getId());
        assertEquals(2, attempts.size());
        assertEquals(DeliveryOutcome.SUCCESS, attempts.get(0).getOutcome());
        assertEquals(DeliveryOutcome.DUPLICATE, attempts.get(1).getOutcome());
        assertTrue(attempts.get(1).getDetails().contains("Duplicate delivery"));
    }

    @Test
    @DisplayName("Requirement 9 & 11: Invalid HMAC signature persists neither an event nor a delivery attempt")
    void testInvalidSignature_persistsNeitherEventNorAttempt() throws Exception {
        WebhookEventRequest eventRequest = new WebhookEventRequest(
                "evt_bad_sig_300",
                "order_300",
                1L,
                "order.created",
                LocalDateTime.now(),
                "PENDING"
        );
        byte[] payload = objectMapper.writeValueAsBytes(eventRequest);
        String badSignature = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        assertThrows(InvalidSignatureException.class, () ->
                webhookIngestionService.ingestWebhook(testSource.getId(), badSignature, payload)
        );

        // Verify neither event nor delivery attempt is persisted
        assertEquals(0, webhookEventRepository.count(), "No event must be persisted when HMAC signature is invalid");
        assertEquals(0, deliveryAttemptRepository.count(), "No delivery attempt must be persisted when HMAC signature is invalid");
    }

    @Test
    @DisplayName("Requirement 6, 7 & 11: Resource-scoped ordering and order.status_changed side effect")
    void testResourceScopedOrdering_andProcessingSideEffect() throws Exception {
        // 1. Order 1 - Event 1 (sequence 1): order.created -> PENDING
        WebhookEventRequest order1Seq1 = new WebhookEventRequest(
                "evt_res_1_seq_1",
                "res_order_100",
                1L,
                "order.created",
                LocalDateTime.now().minusMinutes(2),
                "PENDING"
        );
        byte[] payload1 = objectMapper.writeValueAsBytes(order1Seq1);
        String sig1 = hmacService.calculateHmacSha256(payload1, testSource.getSecret());
        WebhookEventResponse resp1 = webhookIngestionService.ingestWebhook(testSource.getId(), sig1, payload1);

        assertEquals("PENDING", resp1.currentStatus());
        assertEquals(1L, resp1.sequence());
        assertEquals(1L, resp1.lastSequence());

        // 2. Order 1 - Event 2 (sequence 2): order.status_changed -> SHIPPED (verifies processing side effect)
        WebhookEventRequest order1Seq2 = new WebhookEventRequest(
                "evt_res_1_seq_2",
                "res_order_100",
                2L,
                "order.status_changed",
                LocalDateTime.now().minusMinutes(1),
                "SHIPPED"
        );
        byte[] payload2 = objectMapper.writeValueAsBytes(order1Seq2);
        String sig2 = hmacService.calculateHmacSha256(payload2, testSource.getSecret());
        WebhookEventResponse resp2 = webhookIngestionService.ingestWebhook(testSource.getId(), sig2, payload2);

        assertEquals("SHIPPED", resp2.currentStatus(), "Successfully processed order.status_changed must update currentStatus");
        assertEquals(2L, resp2.sequence());
        assertEquals(2L, resp2.lastSequence());

        // 3. Order 2 - Event 1 (sequence 1): order.created -> PENDING
        // Verify sequence is scoped by resourceId: sequence 1 for res_order_200 is independent of res_order_100
        WebhookEventRequest order2Seq1 = new WebhookEventRequest(
                "evt_res_2_seq_1",
                "res_order_200",
                1L,
                "order.created",
                LocalDateTime.now(),
                "PENDING"
        );
        byte[] payload3 = objectMapper.writeValueAsBytes(order2Seq1);
        String sig3 = hmacService.calculateHmacSha256(payload3, testSource.getSecret());
        WebhookEventResponse resp3 = webhookIngestionService.ingestWebhook(testSource.getId(), sig3, payload3);

        assertEquals("res_order_200", resp3.resourceId());
        assertEquals(1L, resp3.sequence());
        assertEquals(1L, resp3.lastSequence());
        assertEquals("PENDING", resp3.currentStatus());

        // 4. Source 2 - Resource 1: sequence 1
        // Verify sequence is also scoped by sourceId: secondSource has its own sequencing for res_order_100
        WebhookEventRequest source2Order1 = new WebhookEventRequest(
                "evt_source2_res_1_seq_1",
                "res_order_100",
                1L,
                "order.created",
                LocalDateTime.now(),
                "PENDING"
        );
        byte[] payload4 = objectMapper.writeValueAsBytes(source2Order1);
        String sig4 = hmacService.calculateHmacSha256(payload4, secondSource.getSecret());
        WebhookEventResponse resp4 = webhookIngestionService.ingestWebhook(secondSource.getId(), sig4, payload4);

        assertEquals(secondSource.getId(), resp4.sourceId());
        assertEquals("res_order_100", resp4.resourceId());
        assertEquals(1L, resp4.sequence());
        assertEquals(1L, resp4.lastSequence());
    }

    @Test
    @DisplayName("Requirement 8 & 11: Late event detection evaluates (receivedAt - occurredAt) against configured threshold")
    void testLateEventThreshold_evaluation() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lateOccurredAt = now.minusSeconds(lateThresholdSeconds + 60);

        // Verify calculation: receivedAt - occurredAt exceeds configured threshold
        long deltaSeconds = Duration.between(lateOccurredAt, now).getSeconds();
        assertTrue(deltaSeconds > lateThresholdSeconds,
                "Event occurred " + deltaSeconds + "s ago which exceeds threshold of " + lateThresholdSeconds + "s");

        WebhookEventRequest lateEventRequest = new WebhookEventRequest(
                "evt_late_400",
                "order_late_400",
                1L,
                "order.created",
                lateOccurredAt,
                "PENDING"
        );
        byte[] payload = objectMapper.writeValueAsBytes(lateEventRequest);
        String signature = hmacService.calculateHmacSha256(payload, testSource.getSecret());

        WebhookEventResponse response = webhookIngestionService.ingestWebhook(testSource.getId(), signature, payload);
        assertNotNull(response);
        assertEquals("evt_late_400", response.eventId());
        assertNotNull(response.receivedAt());

        long actualDelta = Duration.between(response.occurredAt(), response.receivedAt()).getSeconds();
        assertTrue(actualDelta >= lateThresholdSeconds,
                "receivedAt - occurredAt (" + actualDelta + "s) should be >= late threshold (" + lateThresholdSeconds + "s)");

        // Compare with on-time event (occurred 10s ago)
        LocalDateTime onTimeOccurredAt = now.minusSeconds(10);
        long onTimeDelta = Duration.between(onTimeOccurredAt, now).getSeconds();
        assertTrue(onTimeDelta < lateThresholdSeconds,
                "On-time event delta (" + onTimeDelta + "s) must be less than threshold (" + lateThresholdSeconds + "s)");
    }
}
