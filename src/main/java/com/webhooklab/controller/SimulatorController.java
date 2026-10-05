package com.webhooklab.controller;

import com.webhooklab.dto.SimulatorRequest;
import com.webhooklab.dto.SimulatorResponse;
import com.webhooklab.service.SimulatorService;
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
public class SimulatorController {

    private final SimulatorService simulatorService;

    @PostMapping("/send")
    public ResponseEntity<SimulatorResponse> send(
            @Valid @RequestBody SimulatorRequest request,
            Authentication authentication
    ) {
        SimulatorResponse response = simulatorService.simulate(request, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
