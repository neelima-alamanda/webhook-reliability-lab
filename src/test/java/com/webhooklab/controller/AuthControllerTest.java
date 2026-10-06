package com.webhooklab.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhooklab.dto.AuthResponse;
import com.webhooklab.dto.LoginRequest;
import com.webhooklab.dto.RegisterRequest;
import com.webhooklab.entity.Role;
import com.webhooklab.exception.DuplicateResourceException;
import com.webhooklab.exception.GlobalExceptionHandler;
import com.webhooklab.repository.UserRepository;
import com.webhooklab.security.JwtAuthenticationFilter;
import com.webhooklab.security.JwtService;
import com.webhooklab.security.PasswordEncoderConfig;
import com.webhooklab.security.SecurityConfig;
import com.webhooklab.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/auth/register with valid input returns 201 Created and token")
    void testRegister_success_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "securePassword123");
        AuthResponse response = new AuthResponse("mock.jwt.token", "alice", Role.USER);

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/register with blank username returns 400 Bad Request")
    void testRegister_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("", "alice@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.username").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid email format returns 400 Bad Request")
    void testRegister_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "not-a-valid-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register with blank password returns 400 Bad Request")
    void testRegister_blankPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "   ");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate username returns 409 Conflict")
    void testRegister_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        when(authService.register(any())).thenThrow(new DuplicateResourceException("Username is already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email returns 409 Conflict")
    void testRegister_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        when(authService.register(any())).thenThrow(new DuplicateResourceException("Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/register with malformed JSON body returns 400 Bad Request")
    void testRegister_malformedJson_returns400() throws Exception {
        String malformedJson = "{ \"username\": \"alice\", \"email\": }";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid or malformed JSON request body"));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid input returns 200 OK and token")
    void testLogin_success_returns200() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");
        AuthResponse response = new AuthResponse("mock.jwt.token", "alice", Role.USER);

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @DisplayName("POST /api/auth/login with blank email returns 400 Bad Request")
    void testLogin_blankEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login with invalid email format returns 400 Bad Request")
    void testLogin_invalidEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest("invalid-email", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login with blank password returns 400 Bad Request")
    void testLogin_blankPassword_returns400() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "  ");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login with bad credentials returns 401 Unauthorized")
    void testLogin_badCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongPassword");

        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login with unknown username/email returns 401 Unauthorized without exposing user existence")
    void testLogin_usernameNotFound_returns401() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");

        when(authService.login(any())).thenThrow(new UsernameNotFoundException("User not found: nonexistent@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
