package com.finsight.service;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.repository.InMemoryTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TradeServiceImplTest {

    private InMemoryTradeRepository repository;
    private TradeServiceImpl tradeService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTradeRepository();
        tradeService = new TradeServiceImpl(repository);
    }

    @Test
    @DisplayName("Should create trade successfully with auto-generated ID and default timestamp")
    void createTrade_Success() {
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.50"), "TRADER_001", null, TradeStatus.EXECUTED
        );

        TradeResponse response = tradeService.createTrade(request);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("AAPL", response.getSymbol());
        assertEquals(TradeSide.BUY, response.getSide());
        assertEquals(100L, response.getQuantity());
        assertEquals(new BigDecimal("180.50"), response.getPrice());
        assertEquals("TRADER_001", response.getTraderId());
        assertNotNull(response.getTimestamp());
        assertEquals(TradeStatus.EXECUTED, response.getStatus());
    }

    @Test
    @DisplayName("Should retrieve trade by ID")
    void getTradeById_Success() {
        CreateTradeRequest request = new CreateTradeRequest(
                "NVDA", TradeSide.BUY, 50L, new BigDecimal("120.00"), "TRADER_002", Instant.now(), TradeStatus.PENDING
        );
        TradeResponse created = tradeService.createTrade(request);

        TradeResponse found = tradeService.getTradeById(created.getId());

        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("NVDA", found.getSymbol());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when querying non-existent trade")
    void getTradeById_NotFound() {
        assertThrows(ResourceNotFoundException.class, () -> tradeService.getTradeById(999L));
    }

    @Test
    @DisplayName("Should retrieve all trades")
    void getAllTrades_Success() {
        tradeService.createTrade(new CreateTradeRequest("AAPL", TradeSide.BUY, 100L, new BigDecimal("150.00"), "T1", Instant.now(), TradeStatus.EXECUTED));
        tradeService.createTrade(new CreateTradeRequest("MSFT", TradeSide.SELL, 200L, new BigDecimal("400.00"), "T2", Instant.now(), TradeStatus.PENDING));

        List<TradeResponse> trades = tradeService.getAllTrades();

        assertEquals(2, trades.size());
    }

    @Test
    @DisplayName("Should update trade successfully")
    void updateTrade_Success() {
        TradeResponse created = tradeService.createTrade(new CreateTradeRequest("AAPL", TradeSide.BUY, 100L, new BigDecimal("150.00"), "T1", Instant.now(), TradeStatus.PENDING));

        UpdateTradeRequest updateRequest = new UpdateTradeRequest("AAPL", TradeSide.BUY, 150L, new BigDecimal("155.00"), "T1", Instant.now(), TradeStatus.EXECUTED);
        TradeResponse updated = tradeService.updateTrade(created.getId(), updateRequest);

        assertEquals(150L, updated.getQuantity());
        assertEquals(new BigDecimal("155.00"), updated.getPrice());
        assertEquals(TradeStatus.EXECUTED, updated.getStatus());
    }

    @Test
    @DisplayName("Should delete trade successfully")
    void deleteTrade_Success() {
        TradeResponse created = tradeService.createTrade(new CreateTradeRequest("GOOGL", TradeSide.BUY, 10L, new BigDecimal("2800.00"), "T3", Instant.now(), TradeStatus.EXECUTED));

        tradeService.deleteTrade(created.getId());

        assertThrows(ResourceNotFoundException.class, () -> tradeService.getTradeById(created.getId()));
    }

    @Test
    @DisplayName("Concurrency Test: 100 concurrent trade creations produce unique IDs without collision or race conditions")
    void concurrentCreateTrades_ThreadSafety_NoIdCollisions() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<TradeResponse> createdTrades = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for sync start line
                    CreateTradeRequest request = new CreateTradeRequest(
                            "SYM_" + index,
                            TradeSide.BUY,
                            (long) (index + 1),
                            new BigDecimal("100.00"),
                            "TRADER_" + index,
                            Instant.now(),
                            TradeStatus.EXECUTED
                    );
                    TradeResponse response = tradeService.createTrade(request);
                    generatedIds.add(response.getId());
                    createdTrades.add(response);
                } catch (Exception e) {
                    fail("Concurrent trade creation failed with exception: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "All concurrent tasks should complete within timeout");
        assertEquals(threadCount, createdTrades.size(), "Total created trade responses should match thread count");
        assertEquals(threadCount, generatedIds.size(), "All generated IDs must be unique (no ID collisions)");
        assertEquals(threadCount, tradeService.getAllTrades().size(), "In-memory repository size must equal total created trades");
    }
}
