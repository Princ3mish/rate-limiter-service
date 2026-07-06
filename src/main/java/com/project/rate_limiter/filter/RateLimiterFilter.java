package com.project.rate_limiter.filter;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.project.rate_limiter.constants.RateLimiterAlgorithm;
import com.project.rate_limiter.entity.RateLimiterDecision;
import com.project.rate_limiter.service.FixedSizeRateLimiterService;
import com.project.rate_limiter.service.SlidingWindowRateLimiterService;
import com.project.rate_limiter.service.TokenBucketRateLimiterService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiterService tokenBucketService;
    private final FixedSizeRateLimiterService fixedSizeService;
    private final SlidingWindowRateLimiterService slidingWindowService;

    public RateLimiterFilter(
            TokenBucketRateLimiterService tokenBucketService,
            FixedSizeRateLimiterService fixedSizeService,
            SlidingWindowRateLimiterService slidingWindowService) {
        this.tokenBucketService = tokenBucketService;
        this.fixedSizeService = fixedSizeService;
        this.slidingWindowService = slidingWindowService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/limiter/api")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = request.getRemoteAddr();
        }

        String algorithmHeader = request.getHeader("X-Limiter-Algorithm");
        RateLimiterAlgorithm algorithm = RateLimiterAlgorithm.TOKEN_BUCKET; // Default fallback

        if (algorithmHeader != null) {
            try {
                algorithm = RateLimiterAlgorithm.valueOf(algorithmHeader.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Keep the default TOKEN_BUCKET if an invalid header value is passed
            }
        }

        RateLimiterDecision decision;
        switch (algorithm) {
            case FIXED_WINDOW -> decision = fixedSizeService.isAllowed(userId);
            case SLIDING_WINDOW -> decision = slidingWindowService.isAllowed(userId);
            default -> decision = tokenBucketService.isAllowed(userId);
        }

        response.setHeader("X-RateLimit-Limit-Type", algorithm.name());
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-TimeToFull-Ms", String.valueOf(decision.timeToFullMs()));

        // 6. Enforce the decision
        if (decision.isAllowed()) {
            // Success: Pass request along to the target API Controller
            filterChain.doFilter(request, response);
        } else {
            // Failed: Return HTTP 429 and bypass the remaining filter chain entirely
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP 429 [5]
            response.setHeader("X-RateLimit-Retry-After-Ms", String.valueOf(decision.retryAfterMs()));
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded under %s algorithm. Please try again after %d ms.\", \"retryAfterMs\": %d}",
                    algorithm.name(),
                    decision.retryAfterMs(),
                    decision.retryAfterMs()
            ));
        }
    }
}