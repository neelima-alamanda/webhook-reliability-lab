package com.webhooklab.controller;

import com.webhooklab.dto.DeliveryAttemptResponse;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.DeliveryOutcome;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.Role;
import com.webhooklab.entity.User;
import com.webhooklab.exception.GlobalExceptionHandler;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.security.JwtAuthenticationFilter;
import com.webhooklab.security.JwtService;
import com.webhooklab.security.PasswordEncoderConfig;
import com.webhooklab.security.SecurityConfig;
import com.webhooklab.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EventController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    private User mockUser;
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        mockUser = new User();
        ReflectionTestUtils.setField(mockUser, "id", 1L);
        mockUser.setEmail("owner@example.com");
        mockUser.setUsername("owner");
        mockUser.setPassword("hashedPassword");
        mockUser.setRole(Role.USER);

        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(mockUser));
        when(jwtService.isTokenValid(VALID_TOKEN, "owner@example.com")).thenReturn(true);
    }

    @Test
    @DisplayName("Unauthorized access: GET /api/events without JWT returns 401")
    void testGetEvents_unauthorized() throws Exception {
        mockMvc.perform(get("/api/events")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthorized access: GET /api/events/{id}/attempts without JWT returns 401")
    void testGetAttempts_unauthorized() throws Exception {
        mockMvc.perform(get("/api/events/1/attempts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated owner can view events: GET /api/events returns 200")
    void testGetEvents_authenticatedOwner() throws Exception {
        WebhookEventResponse eventResponse = new WebhookEventResponse(
                100L, 10L, "evt_100", "res_1", 1L, "order.created",
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(4),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        when(eventService.getEvents(null, null, "owner@example.com")).thenReturn(List.of(eventResponse));

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].eventId").value("evt_100"))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"));
    }

    @Test
    @DisplayName("Status filtering: GET /api/events?status=DELIVERED returns filtered events")
    void testGetEvents_filterByStatus() throws Exception {
        WebhookEventResponse eventResponse = new WebhookEventResponse(
                100L, 10L, "evt_100", "res_1", 1L, "order.created",
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().minusMinutes(4),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        when(eventService.getEvents(null, EventStatus.DELIVERED, "owner@example.com"))
                .thenReturn(List.of(eventResponse));

        mockMvc.perform(get("/api/events")
                        .param("status", "DELIVERED")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELIVERED"));
    }

    @Test
    @DisplayName("User cannot view another user's events: returns 404")
    void testGetEvents_userCannotViewAnotherUsersEvents() throws Exception {
        when(eventService.getEvents(eq(99L), any(), eq("owner@example.com")))
                .thenThrow(new ResourceNotFoundException("Webhook source not found with id: 99"));

        mockMvc.perform(get("/api/events")
                        .param("sourceId", "99")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Webhook source not found with id: 99"));
    }

    @Test
    @DisplayName("Delivery-attempt retrieval: GET /api/events/{id}/attempts returns 200 and attempts")
    void testGetDeliveryAttempts_success() throws Exception {
        DeliveryAttemptResponse attempt1 = new DeliveryAttemptResponse(
                1L, 100L, LocalDateTime.now().minusMinutes(4),
                DeliveryOutcome.SUCCESS, "Delivered successfully"
        );
        DeliveryAttemptResponse attempt2 = new DeliveryAttemptResponse(
                2L, 100L, LocalDateTime.now().minusMinutes(2),
                DeliveryOutcome.DUPLICATE, "Duplicate delivery"
        );
        when(eventService.getDeliveryAttempts(100L, "owner@example.com"))
                .thenReturn(List.of(attempt1, attempt2));

        mockMvc.perform(get("/api/events/100/attempts")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$[1].outcome").value("DUPLICATE"));
    }

    @Test
    @DisplayName("Nonexistent event: GET /api/events/{id}/attempts returns 404")
    void testGetDeliveryAttempts_nonexistentEvent() throws Exception {
        when(eventService.getDeliveryAttempts(999L, "owner@example.com"))
                .thenThrow(new ResourceNotFoundException("Webhook event not found with id: 999"));

        mockMvc.perform(get("/api/events/999/attempts")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Webhook event not found with id: 999"));
    }

    @Test
    @DisplayName("Invalid parameter type: GET /api/events?sourceId=abc returns 400 Bad Request")
    void testGetEvents_invalidSourceIdParameterType_returns400() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("sourceId", "abc")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: sourceId"));
    }

    @Test
    @DisplayName("Invalid enum status: GET /api/events?status=NON_EXISTENT returns 400 Bad Request")
    void testGetEvents_invalidStatusEnum_returns400() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("status", "NON_EXISTENT")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: status"));
    }

    @Test
    @DisplayName("Invalid path variable: GET /api/events/abc/attempts returns 400 Bad Request")
    void testGetDeliveryAttempts_invalidIdPathVariable_returns400() throws Exception {
        mockMvc.perform(get("/api/events/abc/attempts")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: id"));
    }
}
