package com.webhooklab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webhooklab.dto.SimulatorRequest;
import com.webhooklab.dto.SimulatorResponse;
import com.webhooklab.dto.WebhookEventRequest;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import com.webhooklab.security.HmacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulatorServiceTest {

    @Mock
    private WebhookSourceRepository webhookSourceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebhookIngestionService webhookIngestionService;

    @Mock
    private HmacService hmacService;

    private ObjectMapper objectMapper;
    private SimulatorService simulatorService;

    private User owner;
    private User otherUser;
    private WebhookSource ownerSource;
    private WebhookSource otherSource;
    private WebhookEventResponse dummyResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        simulatorService = new SimulatorService(
                webhookSourceRepository,
                userRepository,
                webhookIngestionService,
                hmacService,
                objectMapper
        );
        ReflectionTestUtils.setField(simulatorService, "lateThresholdSeconds", 300L);

        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        owner.setEmail("owner@example.com");

        otherUser = new User();
        ReflectionTestUtils.setField(otherUser, "id", 2L);
        otherUser.setEmail("other@example.com");

        ownerSource = new WebhookSource();
        ReflectionTestUtils.setField(ownerSource, "id", 10L);
        ownerSource.setName("owner-source");
        ownerSource.setSecret("secret_owner");
        ownerSource.setOwner(owner);

        otherSource = new WebhookSource();
        ReflectionTestUtils.setField(otherSource, "id", 20L);
        otherSource.setName("other-source");
        otherSource.setSecret("secret_other");
        otherSource.setOwner(otherUser);

        dummyResponse = new WebhookEventResponse(
                100L, 10L, "evt_sim_1", "res_1", 1L, "order.created",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );
    }

    @Test
    @DisplayName("Scenario NORMAL: sends one valid event through ingestion")
    void testScenarioNormal() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(hmacService.calculateHmacSha256(any(), eq("secret_owner"))).thenReturn("valid_sig");
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(dummyResponse);

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "NORMAL"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("NORMAL", response.scenario());
        assertEquals("SUCCESS", response.status());
        assertEquals(1, response.events().size());
        verify(webhookIngestionService, times(1)).ingestWebhook(eq(10L), eq("valid_sig"), any());
    }

    @Test
    @DisplayName("Scenario DUPLICATE: sends the exact same event twice")
    void testScenarioDuplicate() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(hmacService.calculateHmacSha256(any(), eq("secret_owner"))).thenReturn("valid_sig");
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(dummyResponse);

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_dup", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "DUPLICATE"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("DUPLICATE", response.scenario());
        assertEquals("SUCCESS", response.status());
        assertEquals(2, response.events().size());
        verify(webhookIngestionService, times(2)).ingestWebhook(eq(10L), eq("valid_sig"), any());
    }

    @Test
    @DisplayName("Scenario CONCURRENT_DUPLICATE: submits the same event simultaneously with CountDownLatch")
    void testScenarioConcurrentDuplicate() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(hmacService.calculateHmacSha256(any(), eq("secret_owner"))).thenReturn("valid_sig");
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(dummyResponse);

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_concurrent", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "CONCURRENT_DUPLICATE"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("CONCURRENT_DUPLICATE", response.scenario());
        assertEquals("SUCCESS", response.status());
        assertEquals(2, response.events().size());
        verify(webhookIngestionService, times(2)).ingestWebhook(eq(10L), eq("valid_sig"), any());
    }

    @Test
    @DisplayName("Scenario OUT_OF_ORDER: sends sequence N+1 first, then sequence N second")
    void testScenarioOutOfOrder() throws IOException {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(hmacService.calculateHmacSha256(any(), eq("secret_owner"))).thenReturn("valid_sig");
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(dummyResponse);

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_ooo", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "OUT_OF_ORDER"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("OUT_OF_ORDER", response.scenario());
        assertEquals(2, response.events().size());

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(webhookIngestionService, times(2)).ingestWebhook(eq(10L), eq("valid_sig"), payloadCaptor.capture());

        List<byte[]> payloads = payloadCaptor.getAllValues();
        WebhookEventRequest firstCall = objectMapper.readValue(payloads.get(0), WebhookEventRequest.class);
        WebhookEventRequest secondCall = objectMapper.readValue(payloads.get(1), WebhookEventRequest.class);

        // Sequence N+1 sent first (sequence 2)
        assertEquals(2L, firstCall.sequence());
        assertEquals("evt_sim_ooo_seq2", firstCall.eventId());

        // Sequence N sent second (sequence 1)
        assertEquals(1L, secondCall.sequence());
        assertEquals("evt_sim_ooo", secondCall.eventId());
    }

    @Test
    @DisplayName("Scenario LATE: sends event with occurredAt older than configured threshold")
    void testScenarioLate() throws IOException {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(hmacService.calculateHmacSha256(any(), eq("secret_owner"))).thenReturn("valid_sig");
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(dummyResponse);

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_late", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "LATE"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("LATE", response.scenario());
        assertEquals(1, response.events().size());

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(webhookIngestionService).ingestWebhook(eq(10L), eq("valid_sig"), payloadCaptor.capture());

        WebhookEventRequest sentRequest = objectMapper.readValue(payloadCaptor.getValue(), WebhookEventRequest.class);
        // Late threshold is 300 seconds (5 min); event occurredAt should be at least 300s in the past
        assertTrue(sentRequest.occurredAt().isBefore(LocalDateTime.now().minusSeconds(290)));
    }

    @Test
    @DisplayName("Scenario INVALID_SIGNATURE: sends invalid HMAC signature and records rejection")
    void testScenarioInvalidSignature() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("invalid_hmac_signature_value"), any()))
                .thenThrow(new InvalidSignatureException("Invalid HMAC signature"));

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_bad_sig", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "INVALID_SIGNATURE"
        );

        SimulatorResponse response = simulatorService.simulate(request, "owner@example.com");

        assertNotNull(response);
        assertEquals("INVALID_SIGNATURE", response.scenario());
        assertEquals("REJECTED", response.status());
        assertTrue(response.message().contains("401 Unauthorized"));
        assertTrue(response.events().isEmpty());
    }

    @Test
    @DisplayName("User cannot simulate using another user's source (returns 404)")
    void testSimulate_notOwner_throwsResourceNotFound() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        // Source 20 belongs to otherUser
        when(webhookSourceRepository.findById(20L)).thenReturn(Optional.of(otherSource));

        SimulatorRequest request = new SimulatorRequest(
                20L, "evt_sim_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "NORMAL"
        );

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                simulatorService.simulate(request, "owner@example.com")
        );
        assertTrue(ex.getMessage().contains("Webhook source not found"));
        verifyNoInteractions(webhookIngestionService);
    }

    @Test
    @DisplayName("Unknown scenario throws IllegalArgumentException")
    void testSimulate_unknownScenario() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));

        SimulatorRequest request = new SimulatorRequest(
                10L, "evt_sim_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "INVALID_SCENARIO_NAME"
        );

        assertThrows(IllegalArgumentException.class, () ->
                simulatorService.simulate(request, "owner@example.com")
        );
        verifyNoInteractions(webhookIngestionService);
    }
}
