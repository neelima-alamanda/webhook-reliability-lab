package com.webhooklab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.InvalidPayloadException;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import com.webhooklab.security.HmacService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookIngestionServiceTest {

    @Mock
    private WebhookSourceRepository webhookSourceRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private HmacService hmacService;

    private ObjectMapper objectMapper;
    private Validator validator;
    private WebhookIngestionService ingestionService;

    private WebhookSource sampleSource;
    private String validJson;
    private byte[] validBytes;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        ingestionService = new WebhookIngestionService(
                webhookSourceRepository,
                webhookEventRepository,
                webhookEventService,
                hmacService,
                objectMapper,
                validator
        );

        sampleSource = new WebhookSource();
        sampleSource.setName("test-source");
        sampleSource.setSecret("dummy_secret");

        validJson = """
                {
                    "eventId": "evt_test_101",
                    "resourceId": "res_order_55",
                    "sequence": 1,
                    "type": "order.created",
                    "occurredAt": "2026-10-05T12:00:00",
                    "currentStatus": "PENDING"
                }
                """;
        validBytes = validJson.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when source is not found")
    void testSourceNotFound() {
        when(webhookSourceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                ingestionService.ingestWebhook(999L, "sig", validBytes)
        );

        verifyNoInteractions(webhookEventRepository, webhookEventService, hmacService);
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when signature header is missing or blank")
    void testMissingSignature() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));

        assertThrows(InvalidSignatureException.class, () ->
                ingestionService.ingestWebhook(1L, null, validBytes)
        );

        assertThrows(InvalidSignatureException.class, () ->
                ingestionService.ingestWebhook(1L, "   ", validBytes)
        );

        verifyNoInteractions(webhookEventRepository, webhookEventService);
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when HMAC verification fails")
    void testInvalidSignature() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));
        when(hmacService.verifySignature(validBytes, "dummy_secret", "bad_sig")).thenReturn(false);

        assertThrows(InvalidSignatureException.class, () ->
                ingestionService.ingestWebhook(1L, "bad_sig", validBytes)
        );

        verifyNoInteractions(webhookEventRepository, webhookEventService);
    }

    @Test
    @DisplayName("Should throw InvalidPayloadException when payload is malformed or invalid")
    void testInvalidPayload() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));
        byte[] malformedBytes = "{ invalid json }".getBytes(StandardCharsets.UTF_8);
        when(hmacService.verifySignature(malformedBytes, "dummy_secret", "valid_sig")).thenReturn(true);

        assertThrows(InvalidPayloadException.class, () ->
                ingestionService.ingestWebhook(1L, "valid_sig", malformedBytes)
        );

        byte[] invalidValidationBytes = "{\"eventId\":\"\"}".getBytes(StandardCharsets.UTF_8);
        when(hmacService.verifySignature(invalidValidationBytes, "dummy_secret", "valid_sig")).thenReturn(true);

        assertThrows(InvalidPayloadException.class, () ->
                ingestionService.ingestWebhook(1L, "valid_sig", invalidValidationBytes)
        );

        verifyNoInteractions(webhookEventRepository, webhookEventService);
    }

    @Test
    @DisplayName("Should successfully ingest new webhook event")
    void testSuccessfulIngestion() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));
        when(hmacService.verifySignature(validBytes, "dummy_secret", "valid_sig")).thenReturn(true);
        when(webhookEventRepository.existsBySourceIdAndEventId(any(), eq("evt_test_101"))).thenReturn(false);

        WebhookEventResponse expectedResponse = new WebhookEventResponse(
                10L, 1L, "evt_test_101", "res_order_55", 1L, "order.created",
                LocalDateTime.parse("2026-10-05T12:00:00"), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        when(webhookEventService.createAndDeliverEvent(any(), any(), any())).thenReturn(expectedResponse);

        WebhookEventResponse response = ingestionService.ingestWebhook(1L, "valid_sig", validBytes);

        assertNotNull(response);
        assertEquals(EventStatus.DELIVERED, response.status());
        assertEquals("evt_test_101", response.eventId());
        verify(webhookEventService).createAndDeliverEvent(any(), any(), any());
        verify(webhookEventService, never()).recordDuplicateAttempt(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should record duplicate attempt when event already exists in database")
    void testDuplicatePreCheck() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));
        when(hmacService.verifySignature(validBytes, "dummy_secret", "valid_sig")).thenReturn(true);
        when(webhookEventRepository.existsBySourceIdAndEventId(any(), eq("evt_test_101"))).thenReturn(true);

        WebhookEventResponse expectedResponse = new WebhookEventResponse(
                10L, 1L, "evt_test_101", "res_order_55", 1L, "order.created",
                LocalDateTime.parse("2026-10-05T12:00:00"), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        when(webhookEventService.recordDuplicateAttempt(any(), eq("evt_test_101"), any(), contains("Duplicate delivery")))
                .thenReturn(expectedResponse);

        WebhookEventResponse response = ingestionService.ingestWebhook(1L, "valid_sig", validBytes);

        assertNotNull(response);
        verify(webhookEventService, never()).createAndDeliverEvent(any(), any(), any());
        verify(webhookEventService).recordDuplicateAttempt(any(), eq("evt_test_101"), any(), any());
    }

    @Test
    @DisplayName("Should handle concurrent race condition when unique constraint violation occurs")
    void testConcurrentRaceCondition() {
        when(webhookSourceRepository.findById(1L)).thenReturn(Optional.of(sampleSource));
        when(hmacService.verifySignature(validBytes, "dummy_secret", "valid_sig")).thenReturn(true);
        when(webhookEventRepository.existsBySourceIdAndEventId(any(), eq("evt_test_101"))).thenReturn(false);

        when(webhookEventService.createAndDeliverEvent(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1-evt_test_101' for key 'uk_source_event'"));

        WebhookEventResponse expectedResponse = new WebhookEventResponse(
                10L, 1L, "evt_test_101", "res_order_55", 1L, "order.created",
                LocalDateTime.parse("2026-10-05T12:00:00"), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        when(webhookEventService.recordDuplicateAttempt(any(), eq("evt_test_101"), any(), contains("concurrent race")))
                .thenReturn(expectedResponse);

        WebhookEventResponse response = ingestionService.ingestWebhook(1L, "valid_sig", validBytes);

        assertNotNull(response);
        verify(webhookEventService).createAndDeliverEvent(any(), any(), any());
        verify(webhookEventService).recordDuplicateAttempt(any(), eq("evt_test_101"), any(), contains("concurrent race"));
    }
}
