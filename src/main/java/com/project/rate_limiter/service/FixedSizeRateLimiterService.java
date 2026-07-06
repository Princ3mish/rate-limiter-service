package com.project.rate_limiter.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.entity.UserRequestInfo;

@Service
public class FixedSizeRateLimiterService {

    @Value("${limiter.fixed-window.capacity:5}")
    private int capacity;

    @Value("${limiter.fixed-window.window-size-ms:10000}") // Default 10 seconds window
    private long windowSizeMs;

    // In-memory registry mapping each userId to their active Fixed Window state
    private final Map<String, UserRequestInfo> userWindows = new HashMap<>();

    /**
     * Evaluates whether a request is allowed under the Fixed Window algorithm.
     */
    public synchronized RateLimiterDecision isAllowed(String userId) {
        long now = Instant.now().toEpochMilli();

        // Retrieve the user's active window state or initialize a new one if it's their first request
        UserRequestInfo info = userWindows.computeIfAbsent(userId, k -> {
            UserRequestInfo uri = new UserRequestInfo();
            uri.setLimitWindowStart(now);
            uri.setNumberOfRequestsMade(0);
            return uri;
        });

        if (now - info.getLimitWindowStart() >= windowSizeMs) {
            info.setLimitWindowStart(now);
            info.setNumberOfRequestsMade(0);
        }

        // 2. Evaluate decision
        boolean allowed = false;
        long retryAfterMs = 0;

        if (info.getNumberOfRequestsMade() < capacity) {
            info.setNumberOfRequestsMade(info.getNumberOfRequestsMade() + 1);
            allowed = true;
        } else {
            retryAfterMs = (info.getLimitWindowStart() + windowSizeMs) - now;
        }

        int remaining = Math.max(0, capacity - info.getNumberOfRequestsMade());
        long timeToFullMs = (info.getLimitWindowStart() + windowSizeMs) - now;

        return new RateLimiterDecision(
                allowed,
                remaining,
                retryAfterMs,
                timeToFullMs
        );
    }
}