package com.finsight.surveillance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Model representing a compliance or market abuse surveillance alert.
 * Emitted when incoming real-time trade events match surveillance rules.
 */
public class SurveillanceAlert {

    private String alertId;
    private Long tradeId;
    private String traderId;
    private String symbol;
    private String ruleName;
    private String severity;
    private String description;
    private BigDecimal price;
    private Long quantity;
    private Instant timestamp;

    public SurveillanceAlert() {
    }

    public SurveillanceAlert(Long tradeId, String traderId, String symbol, String ruleName, 
                             String severity, String description, BigDecimal price, Long quantity, Instant timestamp) {
        this.alertId = UUID.randomUUID().toString();
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.symbol = symbol;
        this.ruleName = ruleName;
        this.severity = severity;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getTraderId() {
        return traderId;
    }

    public void setTraderId(String traderId) {
        this.traderId = traderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
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
        SurveillanceAlert that = (SurveillanceAlert) o;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId);
    }

    @Override
    public String toString() {
        return "SurveillanceAlert{" +
                "alertId='" + alertId + '\'' +
                ", tradeId=" + tradeId +
                ", traderId='" + traderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", ruleName='" + ruleName + '\'' +
                ", severity='" + severity + '\'' +
                ", description='" + description + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
