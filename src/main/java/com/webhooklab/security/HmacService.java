package com.webhooklab.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HmacService {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SHA256_PREFIX = "sha256=";

    public String calculateHmacSha256(byte[] data, String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("Secret cannot be null");
        }
        byte[] payload = data != null ? data : new byte[0];
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    public boolean verifySignature(byte[] data, String secret, String receivedSignature) {
        if (secret == null || receivedSignature == null || receivedSignature.isBlank()) {
            return false;
        }

        String expectedSignature = calculateHmacSha256(data, secret);
        String normalizedReceived = receivedSignature.trim().toLowerCase();
        if (normalizedReceived.startsWith(SHA256_PREFIX)) {
            normalizedReceived = normalizedReceived.substring(SHA256_PREFIX.length()).trim();
        }

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                normalizedReceived.getBytes(StandardCharsets.UTF_8)
        );
    }
}
