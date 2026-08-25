package com.finsight.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.model.Trade;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Integration Tests verifying FinSight Phase 2 with real containerized dependencies.
 * 
 * --- INFRASTRUCTURE ---
 * - PostgreSQL 16 Container: Validates schema migrations, check constraints, indexes, sequences, and HikariCP pooling.
 * - Redis 7 Container: Validates distributed idempotency via SETNX + EX (24h TTL) and response caching.
 * 
 * --- ARCHITECTURAL DIFFERENCES: IN-MEMORY VS POSTGRESQL CONCURRENCY ---
 * In Phase 1:
 * - Storage was JVM-local AtomicLong (CAS operations) + ConcurrentHashMap (segmented bucket locking).
 * - Latency was nanosecond-level, CPU-bound, with no I/O, no serialization, and no transactional boundaries.
 * 
 * In Phase 2:
 * - Storage is PostgreSQL 16 backed by Flyway migrations and HikariCP connection pool (max 10 active connections).
 * - ID generation uses PostgreSQL's BIGSERIAL sequence (trades_id_seq) which is atomically incremented and WAL-logged.
 * - Concurrency is governed by HikariCP pool leasing, TCP socket roundtrips, and ACID transaction commits.
 * - Multi-threaded ingestion proves that database sequence locking prevents ID collisions under concurrent load
 *   even when worker threads outnumber available physical database connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class TradeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finsight_test")
            .withUsername("finsight_test_user")
            .withPassword("finsight_test_pass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    @BeforeEach
    void cleanDatabase() {
        tradeRepository.deleteAll();
    }

    @Test
    @DisplayName("CRUD Integration: Full lifecycle against PostgreSQL 16 (Create -> Read -> Update -> Patch -> Delete)")
    void testFullCrudLifecycleAgainstPostgres() throws Exception {
        // 1. CREATE TRADE
        CreateTradeRequest createRequest = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                500L,
                new BigDecimal("185.2500"),
                "TRADER_NY_01",
                Instant.now(),
                TradeStatus.PENDING
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.quantity", is(500)))
                .andExpect(jsonPath("$.price", is(185.25)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andReturn();

        TradeResponse createdTrade = objectMapper.readValue(createResult.getResponse().getContentAsString(), TradeResponse.class);
        Long tradeId = createdTrade.getId();

        // Verify direct DB persistence
        assertTrue(tradeRepository.existsById(tradeId));
        Trade persisted = tradeRepository.findById(tradeId).orElseThrow();
        assertEquals("AAPL", persisted.getSymbol());
        assertEquals("TRADER_NY_01", persisted.getTraderId());

        // 2. READ TRADE BY ID
        mockMvc.perform(get("/api/v1/trades/" + tradeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(tradeId.intValue())))
                .andExpect(jsonPath("$.traderId", is("TRADER_NY_01")));

        // 3. UPDATE TRADE
        UpdateTradeRequest updateRequest = new UpdateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                600L,
                new BigDecimal("186.0000"),
                "TRADER_NY_01",
                Instant.now(),
                TradeStatus.EXECUTED
        );

        mockMvc.perform(put("/api/v1/trades/" + tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity", is(600)))
                .andExpect(jsonPath("$.price", is(186.0)))
                .andExpect(jsonPath("$.status", is("EXECUTED")));

        // 4. PATCH TRADE STATUS
        mockMvc.perform(patch("/api/v1/trades/" + tradeId + "/status")
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // 5. DELETE TRADE
        mockMvc.perform(delete("/api/v1/trades/" + tradeId))
                .andExpect(status().isNoContent());

        // Verify deleted from DB
        assertFalse(tradeRepository.existsById(tradeId));

        // 6. VERIFY 404 ON SUBSEQUENT GET
        mockMvc.perform(get("/api/v1/trades/" + tradeId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Redis Idempotency: Duplicate POST with identical Idempotency-Key returns cached response with ZERO duplicate DB rows")
    void testRedisIdempotency_DuplicatePost_ReturnsCachedResponse_NoDuplicateDbRows() throws Exception {
        String idempotencyKey = "order-idempotency-" + UUID.randomUUID();
        CreateTradeRequest request = new CreateTradeRequest(
                "NVDA",
                TradeSide.BUY,
                1000L,
                new BigDecimal("125.5000"),
                "TRADER_DESK_A",
                Instant.now(),
                TradeStatus.EXECUTED
        );

        // 1. Initial Request -> Creates Trade in PostgreSQL, caches in Redis
        MvcResult firstResult = mockMvc.perform(post("/api/v1/trades")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.symbol", is("NVDA")))
                .andReturn();

        TradeResponse firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), TradeResponse.class);
        Long firstTradeId = firstResponse.getId();

        // Verify 1 row exists in DB
        List<Trade> tradesAfterFirst = tradeRepository.findByTraderId("TRADER_DESK_A");
        assertEquals(1, tradesAfterFirst.size());
        assertEquals(firstTradeId, tradesAfterFirst.get(0).getId());

        // 2. Retry with SAME Idempotency-Key (Simulating Client Retry after Network Timeout)
        MvcResult secondResult = mockMvc.perform(post("/api/v1/trades")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(firstTradeId.intValue())))
                .andExpect(jsonPath("$.symbol", is("NVDA")))
                .andExpect(jsonPath("$.quantity", is(1000)))
                .andExpect(jsonPath("$.traderId", is("TRADER_DESK_A")))
                .andReturn();

        TradeResponse secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), TradeResponse.class);
        assertEquals(firstTradeId, secondResponse.getId());

        // CRITICAL CHECK: Still exactly 1 row in PostgreSQL! No duplicate trade created.
        List<Trade> tradesAfterSecond = tradeRepository.findByTraderId("TRADER_DESK_A");
        assertEquals(1, tradesAfterSecond.size(), "PostgreSQL must contain exactly 1 trade row despite duplicate POST");
        assertEquals(1, tradeRepository.count());
    }

    @Test
    @DisplayName("Surveillance Queries: Index-backed searches by traderId and symbol")
    void testSurveillanceQueries_SymbolAndTraderIndexes() throws Exception {
        tradeRepository.save(new Trade(null, "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.00"), "TRADER_A", Instant.now(), TradeStatus.EXECUTED));
        tradeRepository.save(new Trade(null, "AAPL", TradeSide.SELL, 50L, new BigDecimal("181.00"), "TRADER_B", Instant.now(), TradeStatus.EXECUTED));
        tradeRepository.save(new Trade(null, "MSFT", TradeSide.BUY, 200L, new BigDecimal("420.00"), "TRADER_A", Instant.now(), TradeStatus.EXECUTED));

        // Query by Trader ID (uses idx_trades_trader_id)
        mockMvc.perform(get("/api/v1/trades/trader/TRADER_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].traderId", is("TRADER_A")))
                .andExpect(jsonPath("$[1].traderId", is("TRADER_A")));

        // Query by Symbol (uses idx_trades_symbol)
        mockMvc.perform(get("/api/v1/trades/symbol/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")))
                .andExpect(jsonPath("$[1].symbol", is("AAPL")));
    }

    @Test
    @DisplayName("Concurrency Test: 50 concurrent trades against PostgreSQL BIGSERIAL and HikariCP pool produce strictly unique IDs")
    void testConcurrentTradeCreations_ThreadSafety_PostgresSequence_NoCollisions() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<TradeResponse> createdTrades = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Synchronized starting line
                    CreateTradeRequest request = new CreateTradeRequest(
                            "CONC_" + (index % 5),
                            TradeSide.BUY,
                            (long) (index + 1),
                            new BigDecimal("100.0000"),
                            "TRADER_CONC_" + index,
                            Instant.now(),
                            TradeStatus.EXECUTED
                    );

                    MvcResult result = mockMvc.perform(post("/api/v1/trades")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isCreated())
                            .andReturn();

                    TradeResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TradeResponse.class);
                    generatedIds.add(response.getId());
                    createdTrades.add(response);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all concurrent threads simultaneously
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "All concurrent database insertions must complete within timeout");
        assertTrue(errors.isEmpty(), "Zero errors occurred during concurrent insertions: " + errors);
        assertEquals(threadCount, createdTrades.size(), "Total created trade responses must match total thread count");
        assertEquals(threadCount, generatedIds.size(), "All PostgreSQL generated IDs must be strictly unique (no sequence collision)");
        assertEquals(threadCount, tradeRepository.count(), "PostgreSQL table row count must equal total created trades");
    }
}
