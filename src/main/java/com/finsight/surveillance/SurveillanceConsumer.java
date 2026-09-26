package com.finsight.surveillance;

import com.finsight.event.TradeCreatedEvent;
import com.finsight.websocket.AlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Real-time trade surveillance consumer listening to 'trades.created' Kafka topic.
 * Evaluates streaming trade events against lightweight market abuse detection heuristics.
 *
 * Phase 3 additions:
 * - Every generated alert is persisted to PostgreSQL via AlertPersistenceService.
 * - Every generated alert is broadcast in real-time to connected compliance clients
 *   via AlertWebSocketHandler (/ws/alerts endpoint).
 *
 * --- SURVEILLANCE RULES IMPLEMENTED ---
 * 1. LARGE_VOLUME_SPIKE: Detects outsized order executions (quantity >= 5,000 shares)
 *    that may indicate block order front-running or localized liquidity shocks.
 * 2. RAPID_ORDER_BURST: Detects rapid-fire trade velocity (>= 5 executions within 60 seconds
 *    by the same trader) as an early heuristic for high-frequency layering or quote spoofing.
 */
@Component
public class SurveillanceConsumer {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceConsumer.class);

    public static final long     VOLUME_THRESHOLD           = 5000L;
    public static final int      VELOCITY_TRADE_COUNT_THRESHOLD = 5;
    public static final Duration VELOCITY_WINDOW            = Duration.ofSeconds(60);

    // Thread-safe in-memory alert buffer for compliance inspection & test assertion
    private final ConcurrentLinkedQueue<SurveillanceAlert> alertBuffer = new ConcurrentLinkedQueue<>();

    // In-memory sliding time window tracker per trader
    private final Map<String, ConcurrentLinkedDeque<Instant>> traderActivityWindows = new ConcurrentHashMap<>();

    private final AlertPersistenceService alertPersistenceService;
    private final AlertWebSocketHandler   alertWebSocketHandler;

    public SurveillanceConsumer(AlertPersistenceService alertPersistenceService,
                                AlertWebSocketHandler alertWebSocketHandler) {
        this.alertPersistenceService = alertPersistenceService;
        this.alertWebSocketHandler   = alertWebSocketHandler;
    }

    @KafkaListener(topics = "trades.created", groupId = "finsight-surveillance-group",
                   autoStartup = "${spring.kafka.listener.auto-startup:true}")
    public void processTradeCreated(TradeCreatedEvent event) {
        if (event == null || event.getTradeId() == null) {
            log.warn("Received malformed or null TradeCreatedEvent from Kafka");
            return;
        }

        log.info("Surveillance engine processing TradeCreatedEvent: tradeId={}, symbol={}, quantity={}, price={}, traderId={}",
                event.getTradeId(), event.getSymbol(), event.getQuantity(), event.getPrice(), event.getTraderId());

        // 1. Rule Evaluation: Large Volume Spike
        evaluateVolumeThreshold(event);

        // 2. Rule Evaluation: High Velocity Burst (In-memory Sliding Window)
        evaluateVelocityBurst(event);
    }

    // ── Rule: Large Volume Spike ─────────────────────────────────────────────

    private void evaluateVolumeThreshold(TradeCreatedEvent event) {
        if (event.getQuantity() != null && event.getQuantity() >= VOLUME_THRESHOLD) {
            String desc = String.format("Trade quantity %d exceeds block threshold of %d shares",
                    event.getQuantity(), VOLUME_THRESHOLD);

            SurveillanceAlert alert = new SurveillanceAlert(
                    event.getTradeId(), event.getTraderId(), event.getSymbol(),
                    "LARGE_VOLUME_SPIKE", "HIGH", desc,
                    event.getPrice(), event.getQuantity(), event.getTimestamp()
            );

            dispatchAlert(alert);
        }
    }

    // ── Rule: Rapid Order Burst (sliding window) ─────────────────────────────

    private void evaluateVelocityBurst(TradeCreatedEvent event) {
        if (event.getTraderId() == null || event.getTraderId().isBlank()) return;

        Instant now = event.getTimestamp() != null ? event.getTimestamp() : Instant.now();
        ConcurrentLinkedDeque<Instant> timestamps = traderActivityWindows.computeIfAbsent(
                event.getTraderId(), k -> new ConcurrentLinkedDeque<>()
        );

        timestamps.addLast(now);

        // Evict expired timestamps outside sliding window
        Instant windowCutoff = now.minus(VELOCITY_WINDOW);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowCutoff)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= VELOCITY_TRADE_COUNT_THRESHOLD) {
            String desc = String.format("Trader %s executed %d trades within %d seconds window (Threshold: %d)",
                    event.getTraderId(), timestamps.size(), VELOCITY_WINDOW.toSeconds(), VELOCITY_TRADE_COUNT_THRESHOLD);

            SurveillanceAlert alert = new SurveillanceAlert(
                    event.getTradeId(), event.getTraderId(), event.getSymbol(),
                    "RAPID_ORDER_BURST", "CRITICAL", desc,
                    event.getPrice(), event.getQuantity(), now
            );

            dispatchAlert(alert);
        }
    }

    // ── Alert dispatch: buffer → persist → broadcast ─────────────────────────

    /**
     * Central dispatch pipeline for every generated alert:
     * 1. Add to in-memory buffer (for test assertions and in-process queries)
     * 2. Persist to PostgreSQL via AlertPersistenceService (idempotent)
     * 3. Broadcast via WebSocket to connected compliance clients
     */
    private void dispatchAlert(SurveillanceAlert alert) {
        alertBuffer.add(alert);
        log.warn("[SURVEILLANCE ALERT] Rule: {} | Severity: {} | Trader: {} | Symbol: {} | TradeID: {} | {}",
                alert.getRuleName(), alert.getSeverity(), alert.getTraderId(),
                alert.getSymbol(), alert.getTradeId(), alert.getDescription());

        // Persist to PostgreSQL (idempotent — skips duplicate on Kafka redelivery)
        try {
            alertPersistenceService.persistAlert(alert);
        } catch (Exception e) {
            log.error("Failed to persist SurveillanceAlert [alertId={}]: {}", alert.getAlertId(), e.getMessage(), e);
        }

        // Push to connected WebSocket compliance clients
        try {
            alertWebSocketHandler.broadcastAlert(alert);
        } catch (Exception e) {
            log.error("Failed to broadcast SurveillanceAlert [alertId={}] via WebSocket: {}", alert.getAlertId(), e.getMessage(), e);
        }
    }

    // ── In-memory query accessors (used by tests & AlertController) ──────────

    public List<SurveillanceAlert> getAlerts() {
        return List.copyOf(alertBuffer);
    }

    public List<SurveillanceAlert> getAlertsByTraderId(String traderId) {
        if (traderId == null) return List.of();
        return alertBuffer.stream()
                .filter(a -> traderId.equalsIgnoreCase(a.getTraderId()))
                .collect(Collectors.toList());
    }

    public List<SurveillanceAlert> getAlertsByRule(String ruleName) {
        if (ruleName == null) return List.of();
        return alertBuffer.stream()
                .filter(a -> ruleName.equalsIgnoreCase(a.getRuleName()))
                .collect(Collectors.toList());
    }

    public void clearAlerts() {
        alertBuffer.clear();
        traderActivityWindows.clear();
    }
}
