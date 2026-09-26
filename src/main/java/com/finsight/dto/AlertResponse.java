package com.finsight.dto;

import com.finsight.surveillance.SurveillanceAlertEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only DTO returned by the Alert REST API.
 * Decouples the persistence model from the API surface.
 */
public class AlertResponse {

    private Long id;
    private String alertId;
    private Long tradeId;
    private String traderId;
    private String symbol;
    private String ruleName;
    private String severity;
    private String description;
    private BigDecimal price;
    private Long quantity;
    private Instant eventTime;
    private Instant createdAt;
    private boolean acknowledged;
    private Instant acknowledgedAt;

    public AlertResponse() {}

    public static AlertResponse fromEntity(SurveillanceAlertEntity e) {
        AlertResponse r = new AlertResponse();
        r.id             = e.getId();
        r.alertId        = e.getAlertId();
        r.tradeId        = e.getTradeId();
        r.traderId       = e.getTraderId();
        r.symbol         = e.getSymbol();
        r.ruleName       = e.getRuleName();
        r.severity       = e.getSeverity();
        r.description    = e.getDescription();
        r.price          = e.getPrice();
        r.quantity       = e.getQuantity();
        r.eventTime      = e.getEventTime();
        r.createdAt      = e.getCreatedAt();
        r.acknowledged   = e.isAcknowledged();
        r.acknowledgedAt = e.getAcknowledgedAt();
        return r;
    }

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }

    public String getAlertId()             { return alertId; }
    public void setAlertId(String v)       { this.alertId = v; }

    public Long getTradeId()               { return tradeId; }
    public void setTradeId(Long v)         { this.tradeId = v; }

    public String getTraderId()            { return traderId; }
    public void setTraderId(String v)      { this.traderId = v; }

    public String getSymbol()              { return symbol; }
    public void setSymbol(String v)        { this.symbol = v; }

    public String getRuleName()            { return ruleName; }
    public void setRuleName(String v)      { this.ruleName = v; }

    public String getSeverity()            { return severity; }
    public void setSeverity(String v)      { this.severity = v; }

    public String getDescription()         { return description; }
    public void setDescription(String v)   { this.description = v; }

    public BigDecimal getPrice()           { return price; }
    public void setPrice(BigDecimal v)     { this.price = v; }

    public Long getQuantity()              { return quantity; }
    public void setQuantity(Long v)        { this.quantity = v; }

    public Instant getEventTime()          { return eventTime; }
    public void setEventTime(Instant v)    { this.eventTime = v; }

    public Instant getCreatedAt()          { return createdAt; }
    public void setCreatedAt(Instant v)    { this.createdAt = v; }

    public boolean isAcknowledged()        { return acknowledged; }
    public void setAcknowledged(boolean v) { this.acknowledged = v; }

    public Instant getAcknowledgedAt()     { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant v) { this.acknowledgedAt = v; }
}
