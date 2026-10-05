package com.webhooklab.service;

import com.webhooklab.dto.DeliveryAttemptResponse;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.DeliveryAttempt;
import com.webhooklab.entity.DeliveryOutcome;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.User;
import com.webhooklab.entity.WebhookEvent;
import com.webhooklab.entity.WebhookSource;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.DeliveryAttemptRepository;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.repository.WebhookEventRepository;
import com.webhooklab.repository.WebhookSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private WebhookSourceRepository webhookSourceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventService eventService;

    private User owner;
    private User otherUser;
    private WebhookSource ownerSource;
    private WebhookSource otherSource;
    private WebhookEvent ownerEvent;
    private WebhookEvent otherEvent;

    @BeforeEach
    void setUp() {
        owner = new User();
        ReflectionTestUtils.setField(owner, "id", 1L);
        owner.setEmail("owner@example.com");
        owner.setUsername("owner");

        otherUser = new User();
        ReflectionTestUtils.setField(otherUser, "id", 2L);
        otherUser.setEmail("other@example.com");
        otherUser.setUsername("other");

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

        ownerEvent = new WebhookEvent();
        ReflectionTestUtils.setField(ownerEvent, "id", 100L);
        ownerEvent.setSource(ownerSource);
        ownerEvent.setEventId("evt_owner_100");
        ownerEvent.setResourceId("res_1");
        ownerEvent.setSequence(1L);
        ownerEvent.setType("payment.created");
        ownerEvent.setStatus(EventStatus.DELIVERED);
        ownerEvent.setOccurredAt(LocalDateTime.now().minusMinutes(5));
        ownerEvent.setReceivedAt(LocalDateTime.now().minusMinutes(4));

        otherEvent = new WebhookEvent();
        ReflectionTestUtils.setField(otherEvent, "id", 200L);
        otherEvent.setSource(otherSource);
        otherEvent.setEventId("evt_other_200");
        otherEvent.setResourceId("res_2");
        otherEvent.setSequence(1L);
        otherEvent.setType("order.completed");
        otherEvent.setStatus(EventStatus.DELIVERED);
        ownerEvent.setOccurredAt(LocalDateTime.now().minusMinutes(10));
        ownerEvent.setReceivedAt(LocalDateTime.now().minusMinutes(9));
    }

    @Test
    @DisplayName("Authenticated owner can view all their events across all sources")
    void testGetEvents_asOwner_allSources() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookEventRepository.findBySourceOwnerIdOrderByIdDesc(1L)).thenReturn(List.of(ownerEvent));

        List<WebhookEventResponse> result = eventService.getEvents(null, null, "owner@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("evt_owner_100", result.get(0).eventId());
        assertEquals(10L, result.get(0).sourceId());
        verify(webhookEventRepository).findBySourceOwnerIdOrderByIdDesc(1L);
    }

    @Test
    @DisplayName("Authenticated owner can filter events by status across all sources")
    void testGetEvents_asOwner_filterByStatus() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookEventRepository.findBySourceOwnerIdAndStatusOrderByIdDesc(1L, EventStatus.DELIVERED))
                .thenReturn(List.of(ownerEvent));

        List<WebhookEventResponse> result = eventService.getEvents(null, EventStatus.DELIVERED, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals(EventStatus.DELIVERED, result.get(0).status());
        verify(webhookEventRepository).findBySourceOwnerIdAndStatusOrderByIdDesc(1L, EventStatus.DELIVERED);
    }

    @Test
    @DisplayName("Authenticated owner can view events for a specific owned source")
    void testGetEvents_asOwner_specificSource() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(webhookEventRepository.findBySourceIdOrderByIdDesc(10L)).thenReturn(List.of(ownerEvent));

        List<WebhookEventResponse> result = eventService.getEvents(10L, null, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals("evt_owner_100", result.get(0).eventId());
        verify(webhookEventRepository).findBySourceIdOrderByIdDesc(10L);
    }

    @Test
    @DisplayName("Authenticated owner can view events filtered by both source and status")
    void testGetEvents_asOwner_specificSourceAndStatus() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(10L)).thenReturn(Optional.of(ownerSource));
        when(webhookEventRepository.findBySourceIdAndStatusOrderByIdDesc(10L, EventStatus.DELIVERED))
                .thenReturn(List.of(ownerEvent));

        List<WebhookEventResponse> result = eventService.getEvents(10L, EventStatus.DELIVERED, "owner@example.com");

        assertEquals(1, result.size());
        assertEquals(EventStatus.DELIVERED, result.get(0).status());
        verify(webhookEventRepository).findBySourceIdAndStatusOrderByIdDesc(10L, EventStatus.DELIVERED);
    }

    @Test
    @DisplayName("User cannot view another user's events by passing their sourceId (returns 404)")
    void testGetEvents_userCannotViewAnotherUsersSourceEvents() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        // Source 20 belongs to otherUser (id 2L), not owner (id 1L)
        when(webhookSourceRepository.findById(20L)).thenReturn(Optional.of(otherSource));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                eventService.getEvents(20L, null, "owner@example.com")
        );

        assertTrue(ex.getMessage().contains("Webhook source not found"));
        verify(webhookEventRepository, never()).findBySourceIdOrderByIdDesc(any());
        verify(webhookEventRepository, never()).findBySourceIdAndStatusOrderByIdDesc(any(), any());
    }

    @Test
    @DisplayName("Querying nonexistent sourceId throws ResourceNotFoundException")
    void testGetEvents_nonexistentSource() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookSourceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                eventService.getEvents(999L, null, "owner@example.com")
        );

        verify(webhookEventRepository, never()).findBySourceIdOrderByIdDesc(any());
    }

    @Test
    @DisplayName("Authenticated owner can view delivery attempts for an owned event")
    void testGetDeliveryAttempts_asOwner_success() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookEventRepository.findById(100L)).thenReturn(Optional.of(ownerEvent));

        DeliveryAttempt attempt1 = new DeliveryAttempt();
        ReflectionTestUtils.setField(attempt1, "id", 1L);
        attempt1.setEvent(ownerEvent);
        attempt1.setReceivedAt(LocalDateTime.now().minusMinutes(4));
        attempt1.setOutcome(DeliveryOutcome.SUCCESS);
        attempt1.setDetails("Delivered successfully");

        DeliveryAttempt attempt2 = new DeliveryAttempt();
        ReflectionTestUtils.setField(attempt2, "id", 2L);
        attempt2.setEvent(ownerEvent);
        attempt2.setReceivedAt(LocalDateTime.now().minusMinutes(2));
        attempt2.setOutcome(DeliveryOutcome.DUPLICATE);
        attempt2.setDetails("Duplicate delivery attempt");

        when(deliveryAttemptRepository.findByEventIdOrderByIdAsc(100L)).thenReturn(List.of(attempt1, attempt2));

        List<DeliveryAttemptResponse> attempts = eventService.getDeliveryAttempts(100L, "owner@example.com");

        assertNotNull(attempts);
        assertEquals(2, attempts.size());
        assertEquals(100L, attempts.get(0).eventId());
        assertEquals(DeliveryOutcome.SUCCESS, attempts.get(0).outcome());
        assertEquals("Delivered successfully", attempts.get(0).details());

        assertEquals(100L, attempts.get(1).eventId());
        assertEquals(DeliveryOutcome.DUPLICATE, attempts.get(1).outcome());
        assertEquals("Duplicate delivery attempt", attempts.get(1).details());

        verify(deliveryAttemptRepository).findByEventIdOrderByIdAsc(100L);
    }

    @Test
    @DisplayName("User cannot view delivery attempts of another user's event (returns 404)")
    void testGetDeliveryAttempts_userCannotViewAnotherUsersEventAttempts() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        // otherEvent belongs to otherSource owned by otherUser (id 2L)
        when(webhookEventRepository.findById(200L)).thenReturn(Optional.of(otherEvent));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                eventService.getDeliveryAttempts(200L, "owner@example.com")
        );

        assertTrue(ex.getMessage().contains("Webhook event not found"));
        verify(deliveryAttemptRepository, never()).findByEventIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("Querying delivery attempts for nonexistent event throws ResourceNotFoundException")
    void testGetDeliveryAttempts_nonexistentEvent() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(webhookEventRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                eventService.getDeliveryAttempts(999L, "owner@example.com")
        );

        assertTrue(ex.getMessage().contains("Webhook event not found with id: 999"));
        verify(deliveryAttemptRepository, never()).findByEventIdOrderByIdAsc(any());
    }
}
