package com.finsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.dto.TradeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Distributed idempotency management service utilizing Redis SETNX (setIfAbsent) and TTL.
 * 
 * --- FAILURE MODE SOLVED ---
 * In mission-critical trade surveillance and order capture pipelines, network timeouts 
 * frequently occur between the client and server. If a client submits a trade order (POST /api/v1/trades)
 * and experiences a TCP/HTTP timeout before receiving the 201 Created response, the client cannot
 * determine whether the trade was successfully committed or dropped before arrival.
 * 
 * When the client retries the POST with the same unique 'Idempotency-Key' header:
 * 1. Redis is checked using SETNX with a 24-hour expiration window.
 * 2. If the key already exists and contains the cached response, the previous TradeResponse is returned immediately.
 * 3. PostgreSQL is NOT hit with a duplicate INSERT, preventing duplicate order records, double-risk allocation,
 *    and false-positive market abuse alerts.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:trades:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final String IN_PROGRESS_MARKER = "__IN_PROGRESS__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if a cached TradeResponse already exists for the given idempotency key.
     * 
     * @param idempotencyKey the unique client-supplied idempotency key
     * @return Optional containing the cached TradeResponse if previously processed, empty otherwise
     */
    public Optional<TradeResponse> getCachedResponse(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson == null || IN_PROGRESS_MARKER.equals(cachedJson)) {
                return Optional.empty();
            }
            TradeResponse response = objectMapper.readValue(cachedJson, TradeResponse.class);
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached TradeResponse for key: {}", idempotencyKey, e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis unavailable during idempotency check: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Attempts to acquire an atomic execution lock using Redis SETNX (setIfAbsent) with TTL.
     * 
     * @param idempotencyKey the unique client-supplied idempotency key
     * @return true if lock was acquired (first request), false if key already exists (in-flight or completed)
     */
    public boolean acquireLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS_MARKER, DEFAULT_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis unavailable during lock acquisition: {}. Falling back to non-idempotent execution.", e.getMessage());
            return true;
        }
    }

    /**
     * Persists the serialized TradeResponse in Redis with 24-hour expiration upon successful DB persistence.
     * 
     * @param idempotencyKey the unique client-supplied idempotency key
     * @param response the processed TradeResponse to cache
     */
    public void storeResponse(String idempotencyKey, TradeResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || response == null) {
            return;
        }
        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, DEFAULT_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TradeResponse for idempotency key: {}", idempotencyKey, e);
        } catch (Exception e) {
            log.warn("Redis unavailable while storing idempotency response: {}", e.getMessage());
        }
    }

    /**
     * Releases/evicts the idempotency key if the trade creation fails with an exception,
     * allowing the client to safely retry.
     * 
     * @param idempotencyKey the unique client-supplied idempotency key
     */
    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String redisKey = KEY_PREFIX + idempotencyKey.trim();
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Redis unavailable while releasing lock for key: {}", idempotencyKey, e);
        }
    }
}
