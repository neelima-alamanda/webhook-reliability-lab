package com.webhooklab.controller;

import com.webhooklab.dto.WebhookEventResponse;
import com.webhooklab.service.WebhookIngestionService;
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
public class WebhookController {

    private final WebhookIngestionService webhookIngestionService;

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
