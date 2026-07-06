package com.project.rate_limiter.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.entity.UserRequestInfo;

@Service
public class SlidingWindowRateLimiterService {

    @Value("${limiter.sliding-window.capacity:5}")
    private int capacity;

    @Value("${limiter.sliding-window.window-size-ms:10000}") // Default 10 seconds sliding window
    private long windowSizeMs;

    // In-memory registry mapping each userId to their active Sliding Window state
    private final Map<String, UserRequestInfo> userWindows = new HashMap<>();

    /**
     * Evaluates whether a request is allowed under the Sliding Window Log algorithm.
     */
    public synchronized RateLimiterDecision isAllowed(String userId) {
        long now = Instant.now().toEpochMilli();

        // Retrieve the user's sliding window state or initialize a new one if it's their first request
        UserRequestInfo info = userWindows.computeIfAbsent(userId, k -> {
            UserRequestInfo uri = new UserRequestInfo();
            uri.setRequestList(new ArrayDeque<>());
            return uri;
        });

        if (info.getRequestList() == null) {
            info.setRequestList(new ArrayDeque<>());
        }

        // 1. Prune: Remove all request timestamps that fall outside the current sliding window
        long windowBoundary = now - windowSizeMs;
        while (!info.getRequestList().isEmpty() && info.getRequestList().peekFirst() <= windowBoundary) {
            info.getRequestList().pollFirst();
        }

        // 2. Evaluate decision
        boolean allowed = false;
        long retryAfterMs = 0;

        if (info.getRequestList().size() < capacity) {
            info.getRequestList().addLast(now);
            allowed = true;
        } else {
            // Blocked: user must wait until the oldest request in the current window slides out
            Long oldestRequest = info.getRequestList().peekFirst();
            if (oldestRequest != null) {
                retryAfterMs = (oldestRequest + windowSizeMs) - now;
            } else {
                retryAfterMs = windowSizeMs;
            }
        }

        int remaining = Math.max(0, capacity - info.getRequestList().size());

        // Time to full: when the newest request currently in the queue slides out of the window
        long timeToFullMs = 0;
        Long newestRequest = info.getRequestList().peekLast();
        if (newestRequest != null) {
            timeToFullMs = (newestRequest + windowSizeMs) - now;
        }

        return new RateLimiterDecision(
                allowed,
                remaining,
                retryAfterMs,
                timeToFullMs
        );
    }
}