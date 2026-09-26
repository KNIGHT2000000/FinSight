package com.finsight.integration;

import com.finsight.dto.AlertResponse;
import com.finsight.surveillance.AlertPersistenceService;
import com.finsight.surveillance.SurveillanceAlert;
import com.finsight.surveillance.SurveillanceAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for alert persistence against a real PostgreSQL 18 instance.
 * Verifies V2 Flyway migration, idempotent inserts, query filters, and acknowledgement workflow.
 */
@SpringBootTest
@Testcontainers
class AlertPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finsight_test")
            .withUsername("finsight_user")
            .withPassword("finsight_dev_pass");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl() + "?reWriteBatchedInserts=true&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        // Disable Kafka listener in this test — only testing DB persistence
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        // Disable Redis in this test
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6380"); // non-existent port; IdempotencyService handles gracefully
    }

    @Autowired
    private AlertPersistenceService alertPersistenceService;

    @Autowired
    private SurveillanceAlertRepository alertRepository;

    @BeforeEach
    void cleanUp() {
        alertRepository.deleteAll();
    }

    private SurveillanceAlert makeAlert(String ruleName, String severity, String traderId, String symbol, long quantity) {
        return new SurveillanceAlert(
                100L + (long)(Math.random() * 9000), traderId, symbol, ruleName, severity,
                "Test alert: " + ruleName,
                new BigDecimal("150.00"), quantity, Instant.now()
        );
    }

    @Test
    @DisplayName("V2 Migration: surveillance_alerts table exists and alert can be persisted")
    void testAlertPersistence_BasicInsert() {
        SurveillanceAlert alert = makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_ALPHA", "AAPL", 10000L);

        alertPersistenceService.persistAlert(alert);

        List<AlertResponse> results = alertPersistenceService.findAll();
        assertEquals(1, results.size());

        AlertResponse saved = results.get(0);
        assertEquals(alert.getAlertId(), saved.getAlertId());
        assertEquals("LARGE_VOLUME_SPIKE", saved.getRuleName());
        assertEquals("HIGH", saved.getSeverity());
        assertEquals("TRADER_ALPHA", saved.getTraderId());
        assertEquals("AAPL", saved.getSymbol());
        assertEquals(10000L, saved.getQuantity());
        assertFalse(saved.isAcknowledged());
        assertNull(saved.getAcknowledgedAt());
    }

    @Test
    @DisplayName("Idempotent insert: persisting the same alertId twice produces exactly one row")
    void testAlertPersistence_IdempotentInsert() {
        SurveillanceAlert alert = makeAlert("RAPID_ORDER_BURST", "CRITICAL", "TRADER_BOT", "NVDA", 200L);

        alertPersistenceService.persistAlert(alert);
        alertPersistenceService.persistAlert(alert); // duplicate — should be skipped

        assertEquals(1, alertRepository.count(), "Duplicate alertId must not create a second row");
    }

    @Test
    @DisplayName("Query by traderId returns only alerts for that trader")
    void testFindByTraderId() {
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_A", "AAPL", 6000L));
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_B", "MSFT", 7000L));

        List<AlertResponse> results = alertPersistenceService.findByTraderId("TRADER_A");
        assertEquals(1, results.size());
        assertEquals("TRADER_A", results.get(0).getTraderId());
    }

    @Test
    @DisplayName("Query by symbol returns only alerts for that symbol")
    void testFindBySymbol() {
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_X", "TSLA", 5500L));
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_Y", "AAPL", 6000L));

        List<AlertResponse> results = alertPersistenceService.findBySymbol("TSLA");
        assertEquals(1, results.size());
        assertEquals("TSLA", results.get(0).getSymbol());
    }

    @Test
    @DisplayName("Query by ruleName returns only alerts for that rule")
    void testFindByRuleName() {
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH",     "TRADER_1", "AAPL", 8000L));
        alertPersistenceService.persistAlert(makeAlert("RAPID_ORDER_BURST",  "CRITICAL", "TRADER_2", "NVDA", 100L));

        List<AlertResponse> results = alertPersistenceService.findByRuleName("RAPID_ORDER_BURST");
        assertEquals(1, results.size());
        assertEquals("RAPID_ORDER_BURST", results.get(0).getRuleName());
    }

    @Test
    @DisplayName("Query by severity returns only alerts with matching severity")
    void testFindBySeverity() {
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH",     "TRADER_P", "AAPL", 9000L));
        alertPersistenceService.persistAlert(makeAlert("RAPID_ORDER_BURST",  "CRITICAL", "TRADER_Q", "NVDA", 150L));

        List<AlertResponse> critical = alertPersistenceService.findBySeverity("CRITICAL");
        assertEquals(1, critical.size());
        assertEquals("CRITICAL", critical.get(0).getSeverity());
    }

    @Test
    @DisplayName("Query by time range returns alerts within the specified window")
    void testFindByTimeRange() {
        alertPersistenceService.persistAlert(makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_T", "GOOGL", 5200L));

        Instant from = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant to   = Instant.now().plus(1, ChronoUnit.MINUTES);

        List<AlertResponse> results = alertPersistenceService.findByTimeRange(from, to);
        assertFalse(results.isEmpty(), "Alert created now must fall within -1m to +1m range");
    }

    @Test
    @DisplayName("Acknowledge alert: sets acknowledged=true and records acknowledgedAt timestamp")
    void testAcknowledgeAlert() {
        SurveillanceAlert alert = makeAlert("LARGE_VOLUME_SPIKE", "HIGH", "TRADER_Z", "META", 11000L);
        alertPersistenceService.persistAlert(alert);

        Long dbId = alertRepository.findAll().get(0).getId();

        AlertResponse acknowledged = alertPersistenceService.acknowledgeAlert(dbId);

        assertTrue(acknowledged.isAcknowledged());
        assertNotNull(acknowledged.getAcknowledgedAt());
    }

    @Test
    @DisplayName("Acknowledge alert is idempotent: calling twice does not change acknowledgedAt")
    void testAcknowledgeAlert_Idempotent() {
        SurveillanceAlert alert = makeAlert("RAPID_ORDER_BURST", "CRITICAL", "TRADER_Z2", "AMD", 300L);
        alertPersistenceService.persistAlert(alert);

        Long dbId = alertRepository.findAll().get(0).getId();

        AlertResponse first  = alertPersistenceService.acknowledgeAlert(dbId);
        AlertResponse second = alertPersistenceService.acknowledgeAlert(dbId);

        assertEquals(first.getAcknowledgedAt(), second.getAcknowledgedAt(),
                "Second acknowledgement must not overwrite the original timestamp");
    }

    @Test
    @DisplayName("Null alert is gracefully ignored without throwing")
    void testPersistAlert_NullInput() {
        assertDoesNotThrow(() -> alertPersistenceService.persistAlert(null));
        assertEquals(0, alertRepository.count());
    }
}
