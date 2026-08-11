package com.finsight.dto;

import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for updating an existing trade record.
 */
public class UpdateTradeRequest {

    @NotBlank(message = "Symbol must not be blank")
    private String symbol;

    @NotNull(message = "Trade side (BUY/SELL) is required")
    private TradeSide side;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Long quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    @NotBlank(message = "Trader ID must not be blank")
    private String traderId;

    private Instant timestamp;

    @NotNull(message = "Trade status (PENDING/EXECUTED/REJECTED) is required")
    private TradeStatus status;

    public UpdateTradeRequest() {
    }

    public UpdateTradeRequest(String symbol, TradeSide side, Long quantity, BigDecimal price, String traderId, Instant timestamp, TradeStatus status) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.traderId = traderId;
        this.timestamp = timestamp;
        this.status = status;
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
}
