package com.finsight.surveillance;

import com.finsight.event.TradeCreatedEvent;
import com.finsight.model.TradeSide;
import com.finsight.websocket.AlertWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SurveillanceConsumer rule evaluation logic.
 * AlertPersistenceService and AlertWebSocketHandler are mocked so tests
 * remain fast, hermetic, and independent of PostgreSQL/WebSocket infrastructure.
 */
@ExtendWith(MockitoExtension.class)
class SurveillanceConsumerTest {

    @Mock
    private AlertPersistenceService alertPersistenceService;

    @Mock
    private AlertWebSocketHandler alertWebSocketHandler;

    private SurveillanceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SurveillanceConsumer(alertPersistenceService, alertWebSocketHandler);
        consumer.clearAlerts();
    }

    @Test
    @DisplayName("Large Volume Spike: Flags trade with quantity >= 5000")
    void testVolumeThreshold_TriggerAlert() {
        TradeCreatedEvent blockTrade = new TradeCreatedEvent(
                101L, "AAPL", TradeSide.BUY, 10000L, new BigDecimal("185.00"), "TRADER_WHALE", Instant.now()
        );

        consumer.processTradeCreated(blockTrade);

        List<SurveillanceAlert> alerts = consumer.getAlerts();
        assertEquals(1, alerts.size());

        SurveillanceAlert alert = alerts.get(0);
        assertEquals("LARGE_VOLUME_SPIKE", alert.getRuleName());
        assertEquals("HIGH", alert.getSeverity());
        assertEquals("TRADER_WHALE", alert.getTraderId());
        assertEquals(101L, alert.getTradeId());
        assertEquals(10000L, alert.getQuantity());
        assertTrue(alert.getDescription().contains("exceeds block threshold"));

        // Verify persistence and broadcast were triggered
        verify(alertPersistenceService, times(1)).persistAlert(any(SurveillanceAlert.class));
        verify(alertWebSocketHandler, times(1)).broadcastAlert(any(SurveillanceAlert.class));
    }

    @Test
    @DisplayName("Normal Volume: Trade with quantity < 5000 does not trigger Large Volume Spike alert")
    void testVolumeThreshold_NoAlertForSmallTrade() {
        TradeCreatedEvent smallTrade = new TradeCreatedEvent(
                102L, "AAPL", TradeSide.BUY, 250L, new BigDecimal("185.00"), "TRADER_RETAIL", Instant.now()
        );

        consumer.processTradeCreated(smallTrade);

        List<SurveillanceAlert> alerts = consumer.getAlertsByRule("LARGE_VOLUME_SPIKE");
        assertTrue(alerts.isEmpty());
        verifyNoInteractions(alertPersistenceService, alertWebSocketHandler);
    }

    @Test
    @DisplayName("Rapid Order Burst: 5 rapid executions by same trader within 60s window triggers CRITICAL alert")
    void testRapidOrderBurst_TriggersCriticalAlert() {
        String traderId = "TRADER_HFT_BOT";
        Instant baseTime = Instant.now();

        for (int i = 1; i <= 5; i++) {
            TradeCreatedEvent event = new TradeCreatedEvent(
                    (long) i, "NVDA", TradeSide.BUY, 100L, new BigDecimal("120.00"),
                    traderId, baseTime.plus(i, ChronoUnit.SECONDS)
            );
            consumer.processTradeCreated(event);
        }

        List<SurveillanceAlert> burstAlerts = consumer.getAlertsByRule("RAPID_ORDER_BURST");
        assertEquals(1, burstAlerts.size());

        SurveillanceAlert alert = burstAlerts.get(0);
        assertEquals("RAPID_ORDER_BURST", alert.getRuleName());
        assertEquals("CRITICAL", alert.getSeverity());
        assertEquals(traderId, alert.getTraderId());
        assertTrue(alert.getDescription().contains("5 trades within 60 seconds"));

        // Burst alert must persist and broadcast
        verify(alertPersistenceService, atLeastOnce()).persistAlert(any(SurveillanceAlert.class));
        verify(alertWebSocketHandler, atLeastOnce()).broadcastAlert(any(SurveillanceAlert.class));
    }

    @Test
    @DisplayName("Sliding Window Eviction: Trades older than 60s do not accumulate towards velocity burst")
    void testRapidOrderBurst_SlidingWindowEviction() {
        String traderId = "TRADER_CALM";
        Instant t0 = Instant.now().minus(120, ChronoUnit.SECONDS);

        // 3 old trades 2 minutes ago
        for (int i = 1; i <= 3; i++) {
            consumer.processTradeCreated(new TradeCreatedEvent(
                    (long) i, "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.00"),
                    traderId, t0.plus(i * 5, ChronoUnit.SECONDS)
            ));
        }

        // 2 new trades now
        Instant tNow = Instant.now();
        for (int i = 4; i <= 5; i++) {
            consumer.processTradeCreated(new TradeCreatedEvent(
                    (long) i, "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.00"),
                    traderId, tNow.plus(i, ChronoUnit.SECONDS)
            ));
        }

        // Old trades evicted; only 2 in window — no burst alert
        List<SurveillanceAlert> burstAlerts = consumer.getAlertsByRule("RAPID_ORDER_BURST");
        assertTrue(burstAlerts.isEmpty(), "Expired trades must be evicted and not trigger false positive burst alerts");
    }

    @Test
    @DisplayName("Graceful Handling: Null or malformed events do not throw exceptions")
    void testNullEvent_NoException() {
        assertDoesNotThrow(() -> consumer.processTradeCreated(null));
        assertDoesNotThrow(() -> consumer.processTradeCreated(new TradeCreatedEvent()));
        assertTrue(consumer.getAlerts().isEmpty());
        verifyNoInteractions(alertPersistenceService, alertWebSocketHandler);
    }

    @Test
    @DisplayName("Persistence error does not prevent WebSocket broadcast")
    void testPersistenceFailure_DoesNotBlockBroadcast() {
        doThrow(new RuntimeException("DB unavailable"))
                .when(alertPersistenceService).persistAlert(any());

        TradeCreatedEvent blockTrade = new TradeCreatedEvent(
                999L, "MSFT", TradeSide.SELL, 8000L, new BigDecimal("420.00"), "TRADER_X", Instant.now()
        );

        // Should not throw — error is caught and logged
        assertDoesNotThrow(() -> consumer.processTradeCreated(blockTrade));

        // WebSocket broadcast must still be attempted despite DB failure
        verify(alertWebSocketHandler, times(1)).broadcastAlert(any(SurveillanceAlert.class));
    }
}
