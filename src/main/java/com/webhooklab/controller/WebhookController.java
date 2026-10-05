package com.webhooklab.controller;

import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.service.WebhookIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook Ingestion", description = "Public webhook ingestion endpoint authenticated via HMAC-SHA256 signature (No JWT required)")
public class WebhookController {

    private final WebhookIngestionService webhookIngestionService;

    @Operation(summary = "Ingest a webhook event", description = "Receives raw webhook request bytes and authenticates sender using HMAC-SHA256 signature in X-Webhook-Signature header. Deduplicates by (sourceId, eventId) and records delivery attempts.")
    @PostMapping("/{sourceId}")
    public ResponseEntity<WebhookEventResponse> ingestWebhook(
            @PathVariable Long sourceId,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody
    ) {
        WebhookEventResponse response = webhookIngestionService.ingestWebhook(sourceId, signature, rawBody);
        return ResponseEntity.ok(response);
    }
}
