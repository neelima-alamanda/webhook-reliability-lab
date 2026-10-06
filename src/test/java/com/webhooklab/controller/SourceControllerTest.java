package com.webhooklab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.CreateSourceRequest;
import com.webhooklab.dto.SourceResponse;
import com.webhooklab.entity.Role;
import com.webhooklab.entity.User;
import com.webhooklab.exception.GlobalExceptionHandler;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.security.JwtAuthenticationFilter;
import com.webhooklab.security.JwtService;
import com.webhooklab.security.PasswordEncoderConfig;
import com.webhooklab.security.SecurityConfig;
import com.webhooklab.service.SourceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SourceController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class SourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SourceService sourceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private User mockUser;

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
    @DisplayName("Unauthenticated POST /api/sources returns 401 Unauthorized")
    void testCreateSource_unauthenticated_returns401() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest("my-source");

        mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated GET /api/sources returns 401 Unauthorized")
    void testGetSources_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated POST /api/sources with valid name returns 201 Created and SourceResponse (no secret leaked)")
    void testCreateSource_authenticatedSuccess_returns201() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest("stripe-prod");
        SourceResponse response = new SourceResponse(1L, "stripe-prod", LocalDateTime.now());

        when(sourceService.createSource(any(), eq("owner@example.com"))).thenReturn(response);

        mockMvc.perform(post("/api/sources")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("stripe-prod"))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @DisplayName("Authenticated POST /api/sources with blank name returns 400 Bad Request")
    void testCreateSource_blankName_returns400() throws Exception {
        CreateSourceRequest request = new CreateSourceRequest("   ");

        mockMvc.perform(post("/api/sources")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    @DisplayName("Authenticated POST /api/sources with missing name returns 400 Bad Request")
    void testCreateSource_missingName_returns400() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/sources")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    @DisplayName("Authenticated POST /api/sources with malformed JSON returns 400 Bad Request")
    void testCreateSource_malformedJson_returns400() throws Exception {
        String malformedJson = "{ \"name\": }";

        mockMvc.perform(post("/api/sources")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid or malformed JSON request body"));
    }

    @Test
    @DisplayName("Authenticated GET /api/sources returns 200 OK and sources list")
    void testGetSources_authenticatedSuccess_returns200() throws Exception {
        List<SourceResponse> sources = List.of(
                new SourceResponse(1L, "stripe-prod", LocalDateTime.now()),
                new SourceResponse(2L, "shopify-staging", LocalDateTime.now())
        );

        when(sourceService.getSources("owner@example.com")).thenReturn(sources);

        mockMvc.perform(get("/api/sources")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("stripe-prod"))
                .andExpect(jsonPath("$[1].name").value("shopify-staging"));
    }
}
