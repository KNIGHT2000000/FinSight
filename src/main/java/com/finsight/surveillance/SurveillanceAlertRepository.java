package com.finsight.surveillance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for persisted surveillance alerts.
 * All query methods map directly to indexes created in V2 Flyway migration.
 */
@Repository
public interface SurveillanceAlertRepository extends JpaRepository<SurveillanceAlertEntity, Long> {

    /**
     * Retrieve all alerts ordered by creation time descending.
     */
    List<SurveillanceAlertEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Retrieve all alerts generated for a specific trader.
     * Uses index: idx_alerts_trader_id
     */
    List<SurveillanceAlertEntity> findByTraderIdOrderByCreatedAtDesc(String traderId);

    /**
     * Retrieve all alerts for a specific ticker symbol.
     * Uses index: idx_alerts_symbol
     */
    List<SurveillanceAlertEntity> findBySymbolOrderByCreatedAtDesc(String symbol);

    /**
     * Retrieve all alerts matching a specific surveillance rule.
     * Uses index: idx_alerts_rule_name
     */
    List<SurveillanceAlertEntity> findByRuleNameOrderByCreatedAtDesc(String ruleName);

    /**
     * Retrieve all alerts matching a specific severity level.
     * Uses index: idx_alerts_severity
     */
    List<SurveillanceAlertEntity> findBySeverityOrderByCreatedAtDesc(String severity);

    /**
     * Retrieve alerts within a specific time window for regulatory audit replay.
     * Uses index: idx_alerts_created_at
     */
    List<SurveillanceAlertEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    /**
     * Check for duplicate alert (prevent double-persist on Kafka redelivery).
     */
    boolean existsByAlertId(String alertId);
}
