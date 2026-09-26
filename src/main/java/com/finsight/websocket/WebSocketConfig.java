package com.finsight.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration registering the compliance alert streaming endpoint.
 * 
 * Endpoint: ws://host/ws/alerts
 * 
 * Compliance dashboards and automated monitoring systems connect to this endpoint
 * to receive real-time JSON push notifications as surveillance alerts are generated
 * by the SurveillanceConsumer rule engine.
 *
 * --- DESIGN DECISIONS ---
 * 1. Raw WebSocket (not STOMP/SockJS): Keeps the protocol surface minimal.
 *    External compliance systems (Python, Go, Bloomberg terminals) can connect
 *    with any standard WS client without SockJS polyfill overhead.
 * 2. setAllowedOrigins("*"): Appropriate for an internal surveillance microservice
 *    where network-level security controls (VPC/firewall) govern access.
 *    Restrict to specific origins in production deployments.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;

    public WebSocketConfig(AlertWebSocketHandler alertWebSocketHandler) {
        this.alertWebSocketHandler = alertWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .setAllowedOrigins("*");
    }
}
