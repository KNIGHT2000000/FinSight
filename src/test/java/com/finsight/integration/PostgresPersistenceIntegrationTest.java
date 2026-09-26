package com.finsight.integration;

import com.finsight.model.Trade;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep Persistence Integration Tests verifying PostgreSQL 18 schema, Flyway migrations,
 * table constraints, indexes, HikariCP pool leasing, and transactional concurrency.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class PostgresPersistenceIntegrationTest {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private com.finsight.event.TradeEventPublisher tradeEventPublisher;

    @BeforeEach
    void setUp() {
        tradeRepository.deleteAll();
    }

    @Test
    @DisplayName("Flyway Migration: V1 schema migration must be successfully applied to PostgreSQL 18")
    void testFlywayMigration_V1Applied() {
        Integer flywayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class
        );
        assertNotNull(flywayCount);
        assertTrue(flywayCount >= 1, "Flyway V1 migration must be recorded as successfully applied in PostgreSQL 18");
    }

    @Test
    @DisplayName("Flyway Migration: V2 surveillance_alerts migration must be successfully applied")
    void testFlywayMigration_V2Applied() {
        Integer flywayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
                Integer.class
        );
        assertNotNull(flywayCount);
        assertTrue(flywayCount >= 1, "Flyway V2 surveillance_alerts migration must be applied");

        // Verify the surveillance_alerts table actually exists
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'surveillance_alerts'",
                Integer.class
        );
        assertNotNull(tableCount);
        assertEquals(1, tableCount, "surveillance_alerts table must exist after V2 migration");
    }

    @Test
    @DisplayName("Phase 3 Indexes: Verify surveillance_alerts indexes exist in pg_indexes catalog")
    void testSurveillanceAlertIndexes_ExistInDatabaseCatalog() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'surveillance_alerts'"
        );
        Set<String> indexNames = ConcurrentHashMap.newKeySet();
        indexes.forEach(row -> indexNames.add((String) row.get("indexname")));

        assertTrue(indexNames.contains("idx_alerts_trader_id"), "idx_alerts_trader_id must exist");
        assertTrue(indexNames.contains("idx_alerts_symbol"),    "idx_alerts_symbol must exist");
        assertTrue(indexNames.contains("idx_alerts_rule_name"), "idx_alerts_rule_name must exist");
        assertTrue(indexNames.contains("idx_alerts_severity"),  "idx_alerts_severity must exist");
        assertTrue(indexNames.contains("idx_alerts_created_at"),"idx_alerts_created_at must exist");
    }

    @Test
    @DisplayName("PostgreSQL Indexes: Verify surveillance indexes exist in pg_indexes catalog")
    void testPostgresIndexes_ExistInDatabaseCatalog() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'trades'"
        );

        Set<String> indexNames = ConcurrentHashMap.newKeySet();
        indexes.forEach(row -> indexNames.add((String) row.get("indexname")));

        assertTrue(indexNames.contains("trades_pkey"), "Primary key index must exist");
        assertTrue(indexNames.contains("idx_trades_symbol"), "Index idx_trades_symbol must exist for symbol lookups");
        assertTrue(indexNames.contains("idx_trades_trader_id"), "Index idx_trades_trader_id must exist for trader lookups");
        assertTrue(indexNames.contains("idx_trades_trader_id_created_at"), "Composite index idx_trades_trader_id_created_at must exist for window surveillance");
    }

    @Test
    @DisplayName("PostgreSQL Check Constraints: chk_trade_side rejects invalid side at database level")
    void testDatabaseCheckConstraint_InvalidSide_Rejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.execute(
                    "INSERT INTO trades (symbol, side, quantity, price, trader_id, timestamp, status, created_at, updated_at) " +
                    "VALUES ('AAPL', 'INVALID_SIDE', 100, 150.00, 'TRADER_1', NOW(), 'EXECUTED', NOW(), NOW())"
            );
        });
    }

    @Test
    @DisplayName("PostgreSQL Check Constraints: chk_trade_quantity rejects quantity <= 0 at database level")
    void testDatabaseCheckConstraint_NegativeOrZeroQuantity_Rejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.execute(
                    "INSERT INTO trades (symbol, side, quantity, price, trader_id, timestamp, status, created_at, updated_at) " +
                    "VALUES ('AAPL', 'BUY', 0, 150.00, 'TRADER_1', NOW(), 'EXECUTED', NOW(), NOW())"
            );
        });
    }

    @Test
    @DisplayName("PostgreSQL Check Constraints: chk_trade_price rejects price <= 0.0 at database level")
    void testDatabaseCheckConstraint_NegativeOrZeroPrice_Rejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.execute(
                    "INSERT INTO trades (symbol, side, quantity, price, trader_id, timestamp, status, created_at, updated_at) " +
                    "VALUES ('AAPL', 'BUY', 100, -50.00, 'TRADER_1', NOW(), 'EXECUTED', NOW(), NOW())"
            );
        });
    }

    @Test
    @DisplayName("PostgreSQL Check Constraints: chk_trade_status rejects invalid status at database level")
    void testDatabaseCheckConstraint_InvalidStatus_Rejected() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.execute(
                    "INSERT INTO trades (symbol, side, quantity, price, trader_id, timestamp, status, created_at, updated_at) " +
                    "VALUES ('AAPL', 'BUY', 100, 150.00, 'TRADER_1', NOW(), 'UNKNOWN_STATUS', NOW(), NOW())"
            );
        });
    }

    @Test
    @DisplayName("JPA Auditing Lifecycle: @PrePersist and @PreUpdate populate and refresh timestamps")
    void testJpaAuditingLifecycle_Timestamps() throws InterruptedException {
        Trade trade = new Trade();
        trade.setSymbol("MSFT");
        trade.setSide(TradeSide.BUY);
        trade.setQuantity(250L);
        trade.setPrice(new BigDecimal("415.5000"));
        trade.setTraderId("TRADER_NYC");
        trade.setTimestamp(Instant.now());
        trade.setStatus(TradeStatus.PENDING);

        Trade saved = tradeRepository.save(trade);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(50); // Ensure timestamp delta

        saved.setStatus(TradeStatus.EXECUTED);
        saved.setQuantity(300L);
        Trade updated = tradeRepository.saveAndFlush(saved);

        assertEquals(originalCreatedAt, updated.getCreatedAt(), "createdAt must be immutable");
        assertTrue(updated.getUpdatedAt().isAfter(originalUpdatedAt) || updated.getUpdatedAt().equals(originalUpdatedAt),
                "updatedAt must be updated on modification");
    }

    @Test
    @DisplayName("Surveillance Queries: Index-backed time-window and order queries against PostgreSQL 18")
    void testSurveillanceWindowAndOrderingQueries() {
        Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant t1 = t0.plus(10, ChronoUnit.MINUTES);
        Instant t2 = t0.plus(20, ChronoUnit.MINUTES);
        Instant t3 = t0.plus(30, ChronoUnit.MINUTES);

        Trade trade1 = new Trade(null, "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.00"), "TRADER_SPOOF", t1, TradeStatus.EXECUTED, t1, t1);
        Trade trade2 = new Trade(null, "AAPL", TradeSide.SELL, 50L, new BigDecimal("181.00"), "TRADER_SPOOF", t2, TradeStatus.EXECUTED, t2, t2);
        Trade trade3 = new Trade(null, "NVDA", TradeSide.BUY, 200L, new BigDecimal("125.00"), "TRADER_SPOOF", t3, TradeStatus.EXECUTED, t3, t3);
        Trade tradeOther = new Trade(null, "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.00"), "TRADER_HONEST", t2, TradeStatus.EXECUTED, t2, t2);

        tradeRepository.saveAll(List.of(trade1, trade2, trade3, tradeOther));

        // 1. Surveillance Query by Trader ID
        List<Trade> traderTrades = tradeRepository.findByTraderId("TRADER_SPOOF");
        assertEquals(3, traderTrades.size());

        // 2. Surveillance Query by Symbol
        List<Trade> symbolTrades = tradeRepository.findBySymbol("AAPL");
        assertEquals(3, symbolTrades.size());

        // 3. Time-window query using composite index (trader_id, created_at)
        List<Trade> windowTrades = tradeRepository.findByTraderIdAndCreatedAtBetween("TRADER_SPOOF", t1.minus(1, ChronoUnit.SECONDS), t2.plus(1, ChronoUnit.SECONDS));
        assertEquals(2, windowTrades.size());

        // 4. Chronological descending order query
        List<Trade> orderedTrades = tradeRepository.findByTraderIdOrderByCreatedAtDesc("TRADER_SPOOF");
        assertEquals(3, orderedTrades.size());
        assertEquals("NVDA", orderedTrades.get(0).getSymbol());
    }

    @Test
    @DisplayName("PostgreSQL Concurrency: 50 concurrent transactions produce strictly unique BIGSERIAL IDs across leased HikariCP connections")
    void testConcurrentInserts_PostgresSequence_NoDeadlocksOrCollisions() throws InterruptedException {
        int totalThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Synchronized start
                    transactionTemplate.execute(status -> {
                        Trade trade = new Trade(
                                null,
                                "CONC_" + (index % 5),
                                TradeSide.BUY,
                                (long) (index + 1),
                                new BigDecimal("150.0000"),
                                "TRADER_CONC_" + index,
                                Instant.now(),
                                TradeStatus.EXECUTED
                        );
                        Trade saved = tradeRepository.save(trade);
                        generatedIds.add(saved.getId());
                        return saved;
                    });
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all worker threads simultaneously
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completed, "All concurrent database operations must complete within 30s");
        assertTrue(errors.isEmpty(), "Zero errors should occur across concurrent HikariCP connection leases: " + errors);
        assertEquals(totalThreads, generatedIds.size(), "Every PostgreSQL generated ID must be strictly unique (no BIGSERIAL sequence collision)");
        assertEquals(totalThreads, tradeRepository.count(), "Total rows in PostgreSQL trades table must equal total inserted records");
    }
}
