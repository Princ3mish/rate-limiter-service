package com.project.rate_limiter.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.project.rate_limiter.controller.dto.DemoEvent;
import com.project.rate_limiter.controller.dto.DemoRunRequest;
import com.project.rate_limiter.controller.dto.DemoRunResponse;
import com.project.rate_limiter.helper.ResponseTextHelper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Demo Playground", description = "Run prebuilt scenarios to visualize algorithm tradeoffs")
@RestController
@RequestMapping("/limiter/demo")
public class DemoRunController {

    private final RestTemplate restTemplate;

    @Value("${server.port:8080}")
    private int port;

    @Value("${limiter.token-bucket.capacity:5}")
    private int tokenBucketCapacity;

    @Value("${limiter.token-bucket.refill-rate:1}")
    private int tokenBucketRefillRate;

    @Value("${limiter.fixed-window.capacity:5}")
    private int fixedWindowCapacity;

    @Value("${limiter.fixed-window.window-size-ms:10000}")
    private long fixedWindowSizeMs;

    @Value("${limiter.sliding-window.capacity:5}")
    private int slidingWindowCapacity;

    @Value("${limiter.sliding-window.window-size-ms:10000}")
    private long slidingWindowSizeMs;

    public DemoRunController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Operation(summary = "Simulate Token Bucket Rate Limiting")
    @PostMapping("/token-bucket")
    public ResponseEntity<DemoRunResponse> runTokenBucket(@RequestBody DemoRunRequest request) {
        String userId = request.userId();
        List<DemoEvent> events = new ArrayList<>();
        sendBursts(userId, "TOKEN_BUCKET", 7, events, "Initial rapid burst");

        pause(1500, events, "Pause for 1.5 seconds");

        sendBursts(userId, "TOKEN_BUCKET", 2, events, "Post-pause requests");

        DemoRunResponse response = new DemoRunResponse();
        response.algorithm = "TOKEN_BUCKET";
        response.summary = ResponseTextHelper.buildSummary(events, Map.of(
                "capacity", tokenBucketCapacity,
                "refillRatePerSec", tokenBucketRefillRate
        ));
        response.timeline = ResponseTextHelper.formatTimeline(events);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Simulate Fixed Window Rate Limiting")
    @PostMapping("/fixed-window")
    public ResponseEntity<DemoRunResponse> runFixedWindow(@RequestBody DemoRunRequest request) {
        String userId = request.userId();
        List<DemoEvent> events = new ArrayList<>();
        sendBursts(userId, "FIXED_WINDOW", 6, events, "Initial rapid burst");

        pause(5000, events, "Pause for 5 seconds");

        sendBursts(userId, "FIXED_WINDOW", 3, events, "Post-pause requests");

        DemoRunResponse response = new DemoRunResponse();
        response.algorithm = "FIXED_WINDOW";
        response.summary = ResponseTextHelper.buildSummary(events, Map.of(
                "capacity", fixedWindowCapacity,
                "windowSizeMs", fixedWindowSizeMs
        ));
        response.timeline = ResponseTextHelper.formatTimeline(events);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Simulate Sliding Window Rate Limiting")
    @PostMapping("/sliding-window")
    public ResponseEntity<DemoRunResponse> runSlidingWindow(@RequestBody DemoRunRequest request) {
        String userId = request.userId();
        List<DemoEvent> events = new ArrayList<>();

        sendBursts(userId, "SLIDING_WINDOW", 6, events, "Initial rapid burst");

        pause(3000, events, "Pause for 3 seconds");

        sendBursts(userId, "SLIDING_WINDOW", 2, events, "Post-pause requests");

        DemoRunResponse response = new DemoRunResponse();
        response.algorithm = "SLIDING_WINDOW";
        response.summary = ResponseTextHelper.buildSummary(events, Map.of(
                "capacity", slidingWindowCapacity,
                "windowSizeMs", slidingWindowSizeMs
        ));
        response.timeline = ResponseTextHelper.formatTimeline(events);

        return ResponseEntity.ok(response);
    }

    private void sendBursts(String userId, String algorithm, int count, List<DemoEvent> events, String phaseComment) {
        String url = "http://localhost:" + port + "/limiter/api/check";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-Limiter-Algorithm", algorithm);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (int i = 0; i < count; i++) {
            try {
                ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                long remaining = getHeaderLong(res.getHeaders(), "X-RateLimit-Remaining");
                events.add(new DemoEvent(res.getStatusCode().value(), remaining, 0, phaseComment));
            } catch (HttpStatusCodeException e) {
                long retryAfter = getHeaderLong(e.getResponseHeaders(), "X-RateLimit-Retry-After-Ms");
                events.add(new DemoEvent(e.getStatusCode().value(), 0, retryAfter, phaseComment + " (Blocked)"));
            } catch (Exception e) {
                events.add(new DemoEvent(500, 0, 0, "Error: " + e.getMessage()));
            }
        }
    }

    private void pause(long ms, List<DemoEvent> events, String comment) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        events.add(new DemoEvent(0, 0, 0, "triggered calls at " + (ms / 1000.0) + " sec interval"));
    }

    private long getHeaderLong(HttpHeaders headers, String headerName) {
        if (headers == null) return 0;
        String val = headers.getFirst(headerName);
        if (val == null) return 0;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}