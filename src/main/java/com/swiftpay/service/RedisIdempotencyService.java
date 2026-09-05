package com.swiftpay.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisIdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "swiftpay:idempotency:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisIdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean exists(UUID transactionId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(buildKey(transactionId))
        );
    }

    public boolean reserve(UUID transactionId) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(
                buildKey(transactionId),
                "PROCESSING",
                IDEMPOTENCY_TTL
        );

        return Boolean.TRUE.equals(created);
    }

    public void markCompleted(UUID transactionId) {
        redisTemplate.opsForValue().set(
                buildKey(transactionId),
                "COMPLETED",
                IDEMPOTENCY_TTL
        );
    }

    public void remove(UUID transactionId) {
        redisTemplate.delete(buildKey(transactionId));
    }

    private String buildKey(UUID transactionId) {
        return KEY_PREFIX + transactionId;
    }
}