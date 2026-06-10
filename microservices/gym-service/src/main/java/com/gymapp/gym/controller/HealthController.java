package com.gymapp.gym.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    // Health check propio — rápido, sin dependencias externas
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "gym-service",
                "timestamp", Instant.now().toString()
        ));
    }
}
