package com.finsight.dto;

import com.finsight.model.Trade;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object returned to API consumers representing a trade record.
 * Decoupled cleanly from internal JPA persistence entity.
 */
public class TradeResponse {

    private Long id;
    private String symbol;
    private TradeSide side;
    private Long quantity;
    private BigDecimal price;
    private String traderId;
    private Instant timestamp;
    private TradeStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public TradeResponse() {
    }

    public TradeResponse(Long id, String symbol, TradeSide side, Long quantity, BigDecimal price, 
                         String traderId, Instant timestamp, TradeStatus status) {
        this(id, symbol, side, quantity, price, traderId, timestamp, status, null, null);
    }

    public TradeResponse(Long id, String symbol, TradeSide side, Long quantity, BigDecimal price, 
                         String traderId, Instant timestamp, TradeStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.traderId = traderId;
        this.timestamp = timestamp;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TradeResponse fromEntity(Trade trade) {
        if (trade == null) return null;
        return new TradeResponse(
                trade.getId(),
                trade.getSymbol(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTraderId(),
                trade.getTimestamp(),
                trade.getStatus(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public TradeStatus getStatus() {
        return status;
    }

    public void setStatus(TradeStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
