package com.finsight.surveillance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA Entity representing a persisted market abuse surveillance alert.
 * 
 * Mapped to the Flyway-managed 'surveillance_alerts' table (ddl-auto: none).
 * Alerts are immutable once generated — only the acknowledgement fields may be updated.
 * 
 * --- DECOUPLING DECISION ---
 * No FK constraint to 'trades' table: surveillance alerts must survive independent
 * of trade record deletion (regulatory retention requirements mandate alert history preservation).
 */
@Entity
@Table(name = "surveillance_alerts", indexes = {
        @Index(name = "idx_alerts_trader_id",   columnList = "trader_id"),
        @Index(name = "idx_alerts_symbol",       columnList = "symbol"),
        @Index(name = "idx_alerts_rule_name",    columnList = "rule_name"),
        @Index(name = "idx_alerts_severity",     columnList = "severity"),
        @Index(name = "idx_alerts_created_at",   columnList = "created_at"),
        @Index(name = "idx_alerts_trader_created", columnList = "trader_id, created_at")
})
public class SurveillanceAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "alert_id", nullable = false, unique = true, length = 36)
    private String alertId;

    @Column(name = "trade_id", nullable = false)
    private Long tradeId;

    @Column(name = "trader_id", nullable = false, length = 50)
    private String traderId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "rule_name", nullable = false, length = 60)
    private String ruleName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    public SurveillanceAlertEntity() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    /**
     * Factory method: construct entity from in-memory SurveillanceAlert domain object.
     */
    public static SurveillanceAlertEntity fromAlert(SurveillanceAlert alert) {
        SurveillanceAlertEntity entity = new SurveillanceAlertEntity();
        entity.alertId     = alert.getAlertId();
        entity.tradeId     = alert.getTradeId();
        entity.traderId    = alert.getTraderId();
        entity.symbol      = alert.getSymbol();
        entity.ruleName    = alert.getRuleName();
        entity.severity    = alert.getSeverity();
        entity.description = alert.getDescription();
        entity.price       = alert.getPrice();
        entity.quantity    = alert.getQuantity();
        entity.eventTime   = alert.getTimestamp() != null ? alert.getTimestamp() : Instant.now();
        return entity;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }

    public String getAlertId()             { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public Long getTradeId()               { return tradeId; }
    public void setTradeId(Long tradeId)   { this.tradeId = tradeId; }

    public String getTraderId()                { return traderId; }
    public void setTraderId(String traderId)   { this.traderId = traderId; }

    public String getSymbol()              { return symbol; }
    public void setSymbol(String symbol)   { this.symbol = symbol; }

    public String getRuleName()                { return ruleName; }
    public void setRuleName(String ruleName)   { this.ruleName = ruleName; }

    public String getSeverity()                { return severity; }
    public void setSeverity(String severity)   { this.severity = severity; }

    public String getDescription()             { return description; }
    public void setDescription(String desc)    { this.description = desc; }

    public BigDecimal getPrice()           { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Long getQuantity()              { return quantity; }
    public void setQuantity(Long qty)      { this.quantity = qty; }

    public Instant getEventTime()              { return eventTime; }
    public void setEventTime(Instant t)        { this.eventTime = t; }

    public Instant getCreatedAt()              { return createdAt; }
    public void setCreatedAt(Instant t)        { this.createdAt = t; }

    public boolean isAcknowledged()                { return acknowledged; }
    public void setAcknowledged(boolean acked)     { this.acknowledged = acked; }

    public Instant getAcknowledgedAt()             { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant t)       { this.acknowledgedAt = t; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SurveillanceAlertEntity that = (SurveillanceAlertEntity) o;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() { return Objects.hash(alertId); }

    @Override
    public String toString() {
        return "SurveillanceAlertEntity{id=" + id
                + ", alertId='" + alertId + '\''
                + ", tradeId=" + tradeId
                + ", traderId='" + traderId + '\''
                + ", ruleName='" + ruleName + '\''
                + ", severity='" + severity + '\''
                + '}';
    }
}
