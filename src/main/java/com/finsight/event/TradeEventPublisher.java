package com.finsight.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Event publisher responsible for emitting trade lifecycle events to Apache Kafka.
 * 
 * --- ARCHITECTURAL SCOPE & DEFERRED CAPABILITIES ---
 * 1. Single Topic Architecture ('trades.created'): Keeps event-driven ingestion simple and
 *    focused on real-time surveillance triggers. Multiple topics (e.g., 'trades.cancelled',
 *    'trades.amended', 'market.data') are deferred to subsequent streaming phases.
 * 2. At-Least-Once Delivery: Standard Kafka ack semantics are employed. Dual-write two-phase
 *    commit (2PC) or Outbox Pattern (Debezium CDC) are deferred as this microservice prioritizes
 *    low latency order intake over distributed transaction overhead.
 * 3. JSON Serialization: Schema Registry (Confluent Avro/Protobuf) is intentionally deferred
 *    to avoid coupling to external registry infrastructure in this early deployment tier.
 */
@Component
public class TradeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeEventPublisher.class);
    public static final String TOPIC_TRADES_CREATED = "trades.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TradeEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTradeCreated(TradeCreatedEvent event) {
        if (event == null || event.getTradeId() == null) {
            log.warn("Skipping Kafka event publication: invalid or null TradeCreatedEvent");
            return;
        }

        String key = event.getTradeId().toString();
        log.info("Emitting TradeCreatedEvent to Kafka [topic={}, key={}, symbol={}, traderId={}, quantity={}]",
                TOPIC_TRADES_CREATED, key, event.getSymbol(), event.getTraderId(), event.getQuantity());

        kafkaTemplate.send(TOPIC_TRADES_CREATED, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TradeCreatedEvent to Kafka for tradeId={}: {}", key, ex.getMessage(), ex);
                    } else {
                        log.debug("TradeCreatedEvent acknowledged by Kafka [offset={}, partition={}]",
                                result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
                    }
                });
    }
}
