package com.webhooklab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webhooklab.dto.SimulatorRequest;
import com.webhooklab.dto.SimulatorResponse;
import com.webhooklab.dto.WebhookEventResponse;
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
import com.webhooklab.service.SimulatorService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SimulatorController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class SimulatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimulatorService simulatorService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;
    private User mockUser;
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

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
    @DisplayName("Unauthorized access: POST /api/simulator/send without JWT returns 401")
    void testSend_unauthorized() throws Exception {
        SimulatorRequest request = new SimulatorRequest(
                1L, "evt_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "NORMAL"
        );

        mockMvc.perform(post("/api/simulator/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated simulation: POST /api/simulator/send returns 200 and SimulatorResponse")
    void testSend_authenticatedSuccess() throws Exception {
        SimulatorRequest request = new SimulatorRequest(
                1L, "evt_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "NORMAL"
        );

        WebhookEventResponse eventResponse = new WebhookEventResponse(
                10L, 1L, "evt_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );
        SimulatorResponse expectedResponse = new SimulatorResponse(
                "NORMAL", "SUCCESS", "Simulated normal webhook event delivery", List.of(eventResponse)
        );

        when(simulatorService.simulate(any(), eq("owner@example.com"))).thenReturn(expectedResponse);

        mockMvc.perform(post("/api/simulator/send")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("NORMAL"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.events[0].eventId").value("evt_1"));
    }

    @Test
    @DisplayName("Validation failure: invalid request payload returns 400 Bad Request")
    void testSend_validationFailure() throws Exception {
        // Missing required fields (e.g. blank eventId, null sequence)
        String invalidJson = """
                {
                    "sourceId": 1,
                    "eventId": "",
                    "resourceId": "",
                    "scenario": "NORMAL"
                }
                """;

        mockMvc.perform(post("/api/simulator/send")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    @DisplayName("Source not owned / not found returns 404 Not Found")
    void testSend_sourceNotFound() throws Exception {
        SimulatorRequest request = new SimulatorRequest(
                999L, "evt_1", "res_1", 1L, "order.created",
                LocalDateTime.now(), "PENDING", "NORMAL"
        );

        when(simulatorService.simulate(any(), eq("owner@example.com")))
                .thenThrow(new ResourceNotFoundException("Webhook source not found with id: 999"));

        mockMvc.perform(post("/api/simulator/send")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Webhook source not found with id: 999"));
    }
}
