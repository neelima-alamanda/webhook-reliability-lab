package com.webhooklab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.entity.EventStatus;
import com.webhooklab.exception.GlobalExceptionHandler;
import com.webhooklab.exception.InvalidPayloadException;
import com.webhooklab.exception.InvalidSignatureException;
import com.webhooklab.exception.ResourceNotFoundException;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.security.JwtAuthenticationFilter;
import com.webhooklab.security.JwtService;
import com.webhooklab.security.PasswordEncoderConfig;
import com.webhooklab.security.SecurityConfig;
import com.webhooklab.service.WebhookIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebhookController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookIngestionService webhookIngestionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with valid signature and payload returns 200 OK")
    void testIngestWebhook_success_returns200() throws Exception {
        WebhookEventResponse response = new WebhookEventResponse(
                1L, 10L, "evt_100", "res_1", 1L, "order.created",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now(),
                EventStatus.DELIVERED, "PENDING", 1L
        );

        when(webhookIngestionService.ingestWebhook(eq(10L), eq("valid_sig"), any())).thenReturn(response);

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "valid_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt_100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt_100"))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} without X-Webhook-Signature header returns 401 Unauthorized")
    void testIngestWebhook_missingSignatureHeader_returns401() throws Exception {
        when(webhookIngestionService.ingestWebhook(eq(10L), isNull(), any()))
                .thenThrow(new InvalidSignatureException("Missing X-Webhook-Signature header"));

        mockMvc.perform(post("/api/webhooks/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt_100\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing X-Webhook-Signature header"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with invalid HMAC signature returns 401 Unauthorized")
    void testIngestWebhook_invalidHmacSignature_returns401() throws Exception {
        when(webhookIngestionService.ingestWebhook(eq(10L), eq("bad_sig"), any()))
                .thenThrow(new InvalidSignatureException("Invalid HMAC signature"));

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "bad_sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt_100\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid HMAC signature"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with nonexistent sourceId returns 404 Not Found")
    void testIngestWebhook_sourceNotFound_returns404() throws Exception {
        when(webhookIngestionService.ingestWebhook(eq(999L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Webhook source not found with id: 999"));

        mockMvc.perform(post("/api/webhooks/999")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt_100\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Webhook source not found with id: 999"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with non-numeric sourceId returns 400 Bad Request")
    void testIngestWebhook_invalidSourceIdType_returns400() throws Exception {
        mockMvc.perform(post("/api/webhooks/not-a-number")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt_100\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: sourceId"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with empty body returns 400 Bad Request")
    void testIngestWebhook_emptyBody_returns400() throws Exception {
        when(webhookIngestionService.ingestWebhook(eq(10L), any(), any()))
                .thenThrow(new InvalidPayloadException("Request body cannot be empty"));

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body cannot be empty"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with malformed JSON body returns 400 Bad Request")
    void testIngestWebhook_malformedJson_returns400() throws Exception {
        when(webhookIngestionService.ingestWebhook(eq(10L), any(), any()))
                .thenThrow(new InvalidPayloadException("Invalid or malformed JSON payload"));

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ malformed json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid or malformed JSON payload"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with missing required webhook fields returns 400 Bad Request with details")
    void testIngestWebhook_missingRequiredFields_returns400() throws Exception {
        Map<String, String> fieldErrors = Map.of(
                "eventId", "Event ID is required",
                "resourceId", "Resource ID is required"
        );
        when(webhookIngestionService.ingestWebhook(eq(10L), any(), any()))
                .thenThrow(new InvalidPayloadException("Validation failed", fieldErrors));

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.eventId").value("Event ID is required"))
                .andExpect(jsonPath("$.details.resourceId").value("Resource ID is required"));
    }

    @Test
    @DisplayName("POST /api/webhooks/{sourceId} with invalid event values returns 400 Bad Request with details")
    void testIngestWebhook_invalidEventValues_returns400() throws Exception {
        Map<String, String> fieldErrors = Map.of("sequence", "Sequence must be positive");
        when(webhookIngestionService.ingestWebhook(eq(10L), any(), any()))
                .thenThrow(new InvalidPayloadException("Validation failed", fieldErrors));

        mockMvc.perform(post("/api/webhooks/10")
                        .header("X-Webhook-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sequence\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.sequence").value("Sequence must be positive"));
    }
}
