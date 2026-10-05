package com.webhooklab.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HmacServiceTest {

    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService();
    }

    @Test
    @DisplayName("Should correctly calculate HMAC-SHA256 hex string")
    void testCalculateHmacSha256() {
        String secret = "test_secret_key";
        byte[] data = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);

        String signature = hmacService.calculateHmacSha256(data, secret);
        assertNotNull(signature);
        assertEquals(64, signature.length()); // SHA-256 hex string is 64 characters
    }

    @Test
    @DisplayName("Should verify valid signature with and without sha256= prefix")
    void testVerifySignatureSuccess() {
        String secret = "test_secret_key";
        byte[] data = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);

        String signature = hmacService.calculateHmacSha256(data, secret);

        assertTrue(hmacService.verifySignature(data, secret, signature));
        assertTrue(hmacService.verifySignature(data, secret, "sha256=" + signature));
        assertTrue(hmacService.verifySignature(data, secret, "SHA256=" + signature.toUpperCase()));
    }

    @Test
    @DisplayName("Should reject invalid or altered signatures")
    void testVerifySignatureFailure() {
        String secret = "test_secret_key";
        byte[] data = "{\"eventId\":\"evt_123\"}".getBytes(StandardCharsets.UTF_8);

        assertFalse(hmacService.verifySignature(data, secret, "invalid_signature"));
        assertFalse(hmacService.verifySignature(data, "wrong_secret", hmacService.calculateHmacSha256(data, secret)));
        assertFalse(hmacService.verifySignature(data, secret, null));
        assertFalse(hmacService.verifySignature(data, secret, ""));
    }
}
