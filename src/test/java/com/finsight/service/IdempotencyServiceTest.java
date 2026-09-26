package com.finsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finsight.dto.TradeResponse;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("acquireLock: Returns true when key does not exist (SETNX succeeds)")
    void acquireLock_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotency:trades:order-123"), eq("__IN_PROGRESS__"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean acquired = idempotencyService.acquireLock("order-123");

        assertTrue(acquired);
        verify(valueOperations, times(1)).setIfAbsent(eq("idempotency:trades:order-123"), eq("__IN_PROGRESS__"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("acquireLock: Returns false when key already exists in Redis")
    void acquireLock_AlreadyExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotency:trades:order-123"), eq("__IN_PROGRESS__"), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        boolean acquired = idempotencyService.acquireLock("order-123");

        assertFalse(acquired);
    }

    @Test
    @DisplayName("acquireLock: Graceful fallback to true when Redis throws connection exception")
    void acquireLock_RedisUnavailable_Fallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        boolean acquired = idempotencyService.acquireLock("order-123");

        assertTrue(acquired, "Must fallback to true so trade execution is not blocked if Redis is down");
    }

    @Test
    @DisplayName("getCachedResponse: Returns TradeResponse when valid JSON exists in Redis")
    void getCachedResponse_Found() throws Exception {
        TradeResponse response = new TradeResponse(
                100L, "AAPL", TradeSide.BUY, 500L, new BigDecimal("185.50"), "TRADER_NY", Instant.now(), TradeStatus.EXECUTED
        );
        String json = objectMapper.writeValueAsString(response);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:trades:order-456")).thenReturn(json);

        Optional<TradeResponse> cached = idempotencyService.getCachedResponse("order-456");

        assertTrue(cached.isPresent());
        assertEquals(100L, cached.get().getId());
        assertEquals("AAPL", cached.get().getSymbol());
        assertEquals(new BigDecimal("185.50"), cached.get().getPrice());
        assertEquals("TRADER_NY", cached.get().getTraderId());
    }

    @Test
    @DisplayName("getCachedResponse: Returns empty when key contains __IN_PROGRESS__ marker")
    void getCachedResponse_InProgressMarker_ReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:trades:order-789")).thenReturn("__IN_PROGRESS__");

        Optional<TradeResponse> cached = idempotencyService.getCachedResponse("order-789");

        assertTrue(cached.isEmpty());
    }

    @Test
    @DisplayName("getCachedResponse: Returns empty when key is null or blank")
    void getCachedResponse_BlankKey_ReturnsEmpty() {
        assertTrue(idempotencyService.getCachedResponse(null).isEmpty());
        assertTrue(idempotencyService.getCachedResponse("   ").isEmpty());
    }

    @Test
    @DisplayName("storeResponse: Serializes and saves TradeResponse to Redis with 24h TTL")
    void storeResponse_Success() {
        TradeResponse response = new TradeResponse(
                200L, "NVDA", TradeSide.SELL, 1000L, new BigDecimal("120.00"), "TRADER_LN", Instant.now(), TradeStatus.EXECUTED
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.storeResponse("order-save-1", response);

        verify(valueOperations, times(1)).set(
                eq("idempotency:trades:order-save-1"),
                contains("NVDA"),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("releaseLock: Deletes key from Redis on failure")
    void releaseLock_Success() {
        idempotencyService.releaseLock("order-err-1");

        verify(redisTemplate, times(1)).delete("idempotency:trades:order-err-1");
    }
}
