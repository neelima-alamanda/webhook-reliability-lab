package com.webhooklab.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiConfigTest {

    @Test
    @DisplayName("OpenAPI configuration sets title, description, version, and JWT Bearer security scheme")
    void testCustomOpenAPI() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("Webhook Reliability Lab API", openAPI.getInfo().getTitle());
        assertEquals("Webhook reliability, idempotency and event inspection API", openAPI.getInfo().getDescription());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertNotNull(openAPI.getComponents());
        assertTrue(openAPI.getComponents().getSecuritySchemes().containsKey("Bearer Authentication"));
        assertEquals("bearer", openAPI.getComponents().getSecuritySchemes().get("Bearer Authentication").getScheme());
        assertEquals("JWT", openAPI.getComponents().getSecuritySchemes().get("Bearer Authentication").getBearerFormat());
    }
}
