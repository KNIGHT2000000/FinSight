package com.finsight.event;

import com.finsight.dto.TradeResponse;
import com.finsight.model.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a trade has been successfully ingested and persisted to PostgreSQL.
 * Published to Kafka topic 'trades.created' to drive downstream real-time surveillance rules.
 * 
 * --- DEFERRED ARCHITECTURAL DECISIONS AT THIS STAGE ---
 * 1. Schema Registry (Avro / Protobuf): Deferred in favor of lightweight JSON serialization.
 *    At single-service scope, JSON avoids external schema registry dependencies (e.g., Confluent Schema Registry).
 * 2. Multiple Partitioning Keys: Keyed strictly by String.valueOf(tradeId) to guarantee
 *    partition-level ordering per individual trade without introducing multi-broker routing complexity.
 */
public class TradeCreatedEvent {

    private Long tradeId;
    private String symbol;
    private TradeSide side;
    private Long quantity;
    private BigDecimal price;
    private String traderId;
    private Instant timestamp;

    public TradeCreatedEvent() {
    }

    public TradeCreatedEvent(Long tradeId, String symbol, TradeSide side, Long quantity, 
                             BigDecimal price, String traderId, Instant timestamp) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.traderId = traderId;
        this.timestamp = timestamp;
    }

    public static TradeCreatedEvent fromResponse(TradeResponse response) {
        return new TradeCreatedEvent(
                response.getId(),
                response.getSymbol(),
                response.getSide(),
                response.getQuantity(),
                response.getPrice(),
                response.getTraderId(),
                response.getTimestamp()
        );
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public TradeSide getSide() {
        return side;
    }

    public void setSide(TradeSide side) {
        this.side = side;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getTraderId() {
        return traderId;
    }

    public void setTraderId(String traderId) {
        this.traderId = traderId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeCreatedEvent that = (TradeCreatedEvent) o;
        return Objects.equals(tradeId, that.tradeId) &&
                Objects.equals(symbol, that.symbol) &&
                side == that.side &&
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(price, that.price) &&
                Objects.equals(traderId, that.traderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, symbol, side, quantity, price, traderId);
    }

    @Override
    public String toString() {
        return "TradeCreatedEvent{" +
                "tradeId=" + tradeId +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", quantity=" + quantity +
                ", price=" + price +
                ", traderId='" + traderId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
