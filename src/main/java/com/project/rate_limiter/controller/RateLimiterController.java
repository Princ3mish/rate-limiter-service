package com.project.rate_limiter.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/limiter/api")
public class RateLimiterController {

    @Operation(summary = "Guarded API endpoint used to test rate limiters")
    @GetMapping("/check")
    public ResponseEntity<Map<String, String>> checkApi() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Request successfully bypassed rate-limiting filters!"
        ));
    }
}