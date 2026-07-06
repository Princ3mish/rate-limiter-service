package com.project.rate_limiter.entity;
import lombok.Data;
@Data
public class TokenBucket {
    private int tokens;
    private int capacity;
    private int refillRate;
    private long lastRefillTimestamp;
}
