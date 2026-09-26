package com.finsight.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.surveillance.SurveillanceAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler that maintains active compliance client sessions and
 * broadcasts real-time surveillance alerts as JSON text frames.
 *
 * --- CONCURRENCY MODEL ---
 * CopyOnWriteArraySet ensures thread-safe session management across the
 * Kafka consumer thread (writes alerts) and Spring WebSocket I/O threads
 * (connect / disconnect) without blocking on reads during broadcast.
 *
 * --- BROADCAST CONTRACT ---
 * Called by SurveillanceConsumer.processTradeCreated() after every rule match.
 * Each connected client receives the full SurveillanceAlert JSON payload
 * within the same synchronous call stack as the Kafka consumer thread.
 */
@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);

    private final Set<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    public AlertWebSocketHandler(ObjectMapper objectMapper) {
        // Inject Spring Boot's auto-configured ObjectMapper (already has JavaTimeModule,
        // WRITE_DATES_AS_TIMESTAMPS=false etc.) — avoids redundant duplicate instance.
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        activeSessions.add(session);
        log.info("Compliance client connected [sessionId={}, remoteAddr={}]. Active sessions: {}",
                session.getId(), session.getRemoteAddress(), activeSessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.remove(session);
        log.info("Compliance client disconnected [sessionId={}, status={}]. Active sessions: {}",
                session.getId(), status, activeSessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error [sessionId={}]: {}", session.getId(), exception.getMessage());
        activeSessions.remove(session);
    }

    /**
     * Broadcasts a SurveillanceAlert to all currently connected compliance clients.
     * Serializes the alert as a JSON text frame. Closed or error sessions are
     * evicted during iteration to prevent stale session accumulation.
     *
     * @param alert the real-time market abuse alert to broadcast
     */
    public void broadcastAlert(SurveillanceAlert alert) {
        if (alert == null) return;
        if (activeSessions.isEmpty()) {
            log.debug("No active WebSocket sessions — skipping broadcast for alert {}", alert.getAlertId());
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(alert);
        } catch (IOException e) {
            log.error("Failed to serialize SurveillanceAlert [alertId={}] for WebSocket broadcast: {}",
                    alert.getAlertId(), e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(payload);
        int sent = 0;
        for (WebSocketSession session : activeSessions) {
            if (!session.isOpen()) {
                activeSessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    // Synchronize per-session to serialize concurrent broadcasts safely
                    session.sendMessage(message);
                }
                sent++;
            } catch (IOException e) {
                log.warn("Failed to send alert to session [{}]: {} — evicting session",
                        session.getId(), e.getMessage());
                activeSessions.remove(session);
            }
        }
        log.info("Broadcast SurveillanceAlert [alertId={}, rule={}, severity={}] to {} clients",
                alert.getAlertId(), alert.getRuleName(), alert.getSeverity(), sent);
    }

    /**
     * @return the number of currently connected compliance clients (for health checks / metrics)
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
