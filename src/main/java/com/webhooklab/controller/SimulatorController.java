package com.webhooklab.controller;

import com.webhooklab.dto.SimulatorRequest;
import com.webhooklab.dto.SimulatorResponse;
import com.webhooklab.service.SimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
@Tag(name = "Webhook Simulator", description = "Simulate various webhook delivery scenarios (NORMAL, DUPLICATE, CONCURRENT_DUPLICATE, OUT_OF_ORDER, LATE, INVALID_SIGNATURE) (Requires JWT Bearer token)")
public class SimulatorController {

    private final SimulatorService simulatorService;

    @Operation(summary = "Simulate webhook event delivery", description = "Constructs a simulated webhook payload, generates HMAC signature using the source's stored secret, and routes through the standard ingestion pipeline. Requires Bearer JWT.")
    @PostMapping("/send")
    public ResponseEntity<SimulatorResponse> send(
            @Valid @RequestBody SimulatorRequest request,
            Authentication authentication
    ) {
        SimulatorResponse response = simulatorService.simulate(request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
