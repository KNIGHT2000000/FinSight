package com.finsight.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an executed or submitted trade record in FinSight.
 * 
 * --- TRADE SURVEILLANCE & LIFECYCLE MAPPING ---
 * In institutional trading environments (e.g., NICE Actimize, global investment banks),
 * trade records are captured post-execution or pre-allocation to detect market abuse patterns
 * such as spoofing, layering, insider trading, and wash trading.
 * 
 * - id: System-generated unique audit sequence identifier (used across audit trails).
 * - symbol: Ticker/Financial Instrument identifier (e.g., AAPL, NVDA, EUR/USD).
 * - side: BUY/SELL direction, critical for identifying wash sales or mismatched directional exposure.
 * - quantity: Order volume size. Abnormally high quantities relative to average daily volume trigger volume-spike alerts.
 * - price: Execution unit price. Out-of-band price execution flags potential off-market price manipulation.
 * - traderId: Unique ID of the trading account / trader submitting the order. Crucial for entity aggregation and cross-account behavior analysis.
 * - timestamp: ISO-8601 UTC timestamp of execution. High-precision timing is required for sequence reconstruction (e.g., front-running detection).
 * - status: PENDING, EXECUTED, or REJECTED state tracking order execution state.
 */
public class Trade {

    private Long id;
    private String symbol;
    private TradeSide side;
    private Long quantity;
    private BigDecimal price;
    private String traderId;
    private Instant timestamp;
    private TradeStatus status;

    public Trade() {
    }

    public Trade(Long id, String symbol, TradeSide side, Long quantity, BigDecimal price, String traderId, Instant timestamp, TradeStatus status) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.traderId = traderId;
        this.timestamp = timestamp;
        this.status = status;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return Objects.equals(id, trade.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Trade{" +
                "id=" + id +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", quantity=" + quantity +
                ", price=" + price +
                ", traderId='" + traderId + '\'' +
                ", timestamp=" + timestamp +
                ", status=" + status +
                '}';
    }
}
