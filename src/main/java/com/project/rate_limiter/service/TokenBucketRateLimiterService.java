package com.project.rate_limiter.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.entity.TokenBucket;

@Service
public class TokenBucketRateLimiterService {

    @Value("${limiter.token-bucket.capacity:5}")
    private int defaultCapacity;

    @Value("${limiter.token-bucket.refill-rate:1}") // Tokens refilled per second
    private int defaultRefillRate;

    // In-memory registry mapping each userId to their personal TokenBucket state
    private final Map<String, TokenBucket> buckets = new HashMap<>();

    /**
     * Evaluates whether a request from a user is allowed under the Token Bucket algorithm.
     */
    public synchronized RateLimiterDecision isAllowed(String userId) {
        long now = Instant.now().toEpochMilli();

        // Retrieve the user's bucket or initialize a new one if this is their first request
        TokenBucket bucket = buckets.computeIfAbsent(userId, k -> {
            TokenBucket tb = new TokenBucket();
            tb.setCapacity(defaultCapacity);
            tb.setRefillRate(defaultRefillRate);
            tb.setTokens(defaultCapacity); // Starts completely full
            tb.setLastRefillTimestamp(now);
            return tb;
        });

        // 1. Calculate tokens to add based on elapsed time (Lazy Refill)
        long elapsedMs = now - bucket.getLastRefillTimestamp();
        if (elapsedMs > 0) {
            // Refill tokens: (elapsed milliseconds / 1000 milliseconds) * refillRate per second
            double tokensToAdd = (elapsedMs / 1000.0) * bucket.getRefillRate();
            int newTokens = (int) (bucket.getTokens() + tokensToAdd);

            // Cap the tokens at maximum bucket capacity
            if (newTokens > bucket.getCapacity()) {
                bucket.setTokens(bucket.getCapacity());
            } else if (newTokens > bucket.getTokens()) {
                bucket.setTokens(newTokens);
                // Update timestamp only when tokens are actually incremented
                bucket.setLastRefillTimestamp(now);
            }
        }

        // 2. Evaluate decision
        boolean allowed = false;
        long retryAfterMs = 0;

        if (bucket.getTokens() >= 1) {
            // Deduct one token for the successful request
            bucket.setTokens(bucket.getTokens() - 1);
            allowed = true;
        } else {
            // Calculate how long before at least 1 token is refilled
            double msPerToken = 1000.0 / bucket.getRefillRate();
            long msSinceLastRefill = now - bucket.getLastRefillTimestamp();
            retryAfterMs = (long) Math.max(0, msPerToken - msSinceLastRefill);
        }

        // Calculate time required to refill the entire bucket back to max capacity
        long timeToFullMs = (long) (((bucket.getCapacity() - bucket.getTokens()) * 1000.0) / bucket.getRefillRate());

        return new RateLimiterDecision(
                allowed,
                bucket.getTokens(),
                retryAfterMs,
                timeToFullMs
        );
    }
}