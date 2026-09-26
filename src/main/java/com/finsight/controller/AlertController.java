package com.finsight.controller;

import com.finsight.dto.AlertResponse;
import com.finsight.surveillance.AlertPersistenceService;
import com.finsight.websocket.AlertWebSocketHandler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * REST Controller exposing compliance alert query and acknowledgement endpoints.
 *
 * All read operations are backed by PostgreSQL (via AlertPersistenceService),
 * providing a durable, queryable audit trail of every generated surveillance alert.
 *
 * --- ENDPOINT SUMMARY ---
 * GET  /api/v1/alerts                         – All alerts (optionally filtered)
 * GET  /api/v1/alerts/trader/{traderId}        – Alerts by trader
 * GET  /api/v1/alerts/symbol/{symbol}          – Alerts by symbol
 * GET  /api/v1/alerts/rule/{ruleName}          – Alerts by rule name
 * GET  /api/v1/alerts/severity/{severity}      – Alerts by severity
 * GET  /api/v1/alerts/range?from=...&to=...    – Alerts within time range (ISO-8601)
 * PATCH /api/v1/alerts/{id}/acknowledge        – Acknowledge an alert
 * GET  /api/v1/alerts/status                   – WebSocket connection health
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertPersistenceService alertPersistenceService;
    private final AlertWebSocketHandler   alertWebSocketHandler;

    public AlertController(AlertPersistenceService alertPersistenceService,
                           AlertWebSocketHandler alertWebSocketHandler) {
        this.alertPersistenceService = alertPersistenceService;
        this.alertWebSocketHandler   = alertWebSocketHandler;
    }

    /**
     * Retrieve all persisted surveillance alerts.
     * Supports optional query-param filtering by traderId, symbol, ruleName, or severity.
     *
     * Usage examples:
     *   GET /api/v1/alerts
     *   GET /api/v1/alerts?traderId=TRADER_HFT
     *   GET /api/v1/alerts?symbol=AAPL
     *   GET /api/v1/alerts?ruleName=RAPID_ORDER_BURST
     *   GET /api/v1/alerts?severity=CRITICAL
     */
    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @RequestParam(required = false) String traderId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) String severity) {

        if (traderId != null && !traderId.isBlank()) {
            return ResponseEntity.ok(alertPersistenceService.findByTraderId(traderId));
        }
        if (symbol != null && !symbol.isBlank()) {
            return ResponseEntity.ok(alertPersistenceService.findBySymbol(symbol));
        }
        if (ruleName != null && !ruleName.isBlank()) {
            return ResponseEntity.ok(alertPersistenceService.findByRuleName(ruleName));
        }
        if (severity != null && !severity.isBlank()) {
            return ResponseEntity.ok(alertPersistenceService.findBySeverity(severity));
        }
        return ResponseEntity.ok(alertPersistenceService.findAll());
    }

    /**
     * Retrieve all alerts triggered by a specific trader.
     * Primary surveillance endpoint for trader behavioural profiling.
     */
    @GetMapping("/trader/{traderId}")
    public ResponseEntity<List<AlertResponse>> getAlertsByTrader(@PathVariable String traderId) {
        return ResponseEntity.ok(alertPersistenceService.findByTraderId(traderId));
    }

    /**
     * Retrieve all alerts for a specific market symbol/ticker.
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<AlertResponse>> getAlertsBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(alertPersistenceService.findBySymbol(symbol));
    }

    /**
     * Retrieve all alerts matching a specific surveillance rule.
     * Supported values: LARGE_VOLUME_SPIKE, RAPID_ORDER_BURST
     */
    @GetMapping("/rule/{ruleName}")
    public ResponseEntity<List<AlertResponse>> getAlertsByRule(@PathVariable String ruleName) {
        return ResponseEntity.ok(alertPersistenceService.findByRuleName(ruleName));
    }

    /**
     * Retrieve all alerts matching a specific severity level.
     * Supported values: LOW, MEDIUM, HIGH, CRITICAL
     */
    @GetMapping("/severity/{severity}")
    public ResponseEntity<List<AlertResponse>> getAlertsBySeverity(@PathVariable String severity) {
        return ResponseEntity.ok(alertPersistenceService.findBySeverity(severity));
    }

    /**
     * Retrieve alerts within a specific time range for regulatory audit replay.
     *
     * Usage: GET /api/v1/alerts/range?from=2026-09-01T00:00:00Z&to=2026-09-24T23:59:59Z
     */
    @GetMapping("/range")
    public ResponseEntity<List<AlertResponse>> getAlertsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(alertPersistenceService.findByTimeRange(from, to));
    }

    /**
     * Acknowledge a surveillance alert by its database ID.
     * Records the acknowledgement timestamp and marks it reviewed for audit purposes.
     */
    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledgeAlert(@PathVariable Long id) {
        AlertResponse acknowledged = alertPersistenceService.acknowledgeAlert(id);
        return ResponseEntity.ok(acknowledged);
    }

    /**
     * Health endpoint: returns WebSocket connection status and active session count.
     * Useful for monitoring compliance dashboard connectivity.
     */
    @GetMapping("/status")
    public ResponseEntity<AlertStatusResponse> getAlertStreamStatus() {
        int sessions = alertWebSocketHandler.getActiveSessionCount();
        return ResponseEntity.ok(new AlertStatusResponse(sessions, "ws://host/ws/alerts"));
    }

    // ── Inner record for status response ─────────────────────────────────────

    public record AlertStatusResponse(int activeWebSocketSessions, String webSocketEndpoint) {}
}
