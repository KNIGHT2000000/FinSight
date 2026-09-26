package com.finsight.surveillance;

import com.finsight.dto.AlertResponse;
import com.finsight.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for persisting surveillance alerts to PostgreSQL and
 * servicing read queries from the Alert REST API.
 *
 * --- IDEMPOTENT PERSISTENCE ---
 * Kafka delivers at-least-once. Before inserting, this service checks
 * existsByAlertId (unique UUID) to prevent duplicate rows on redelivery.
 */
@Service
@Transactional(readOnly = true)
public class AlertPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AlertPersistenceService.class);

    private final SurveillanceAlertRepository alertRepository;

    public AlertPersistenceService(SurveillanceAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Persist a new alert to PostgreSQL. No-op if the alertId already exists
     * (guards against Kafka at-least-once redelivery).
     */
    @Transactional
    public void persistAlert(SurveillanceAlert alert) {
        if (alert == null || alert.getAlertId() == null) {
            log.warn("Ignoring null or incomplete SurveillanceAlert — cannot persist");
            return;
        }
        if (alertRepository.existsByAlertId(alert.getAlertId())) {
            log.debug("Alert {} already persisted — skipping duplicate insert", alert.getAlertId());
            return;
        }
        SurveillanceAlertEntity entity = SurveillanceAlertEntity.fromAlert(alert);
        alertRepository.save(entity);
        log.info("Persisted SurveillanceAlert [alertId={}, rule={}, severity={}, trader={}, symbol={}]",
                alert.getAlertId(), alert.getRuleName(), alert.getSeverity(),
                alert.getTraderId(), alert.getSymbol());
    }

    /**
     * Acknowledge an alert by its database row ID.
     * Sets acknowledged=true and records the acknowledgement timestamp.
     */
    @Transactional
    public AlertResponse acknowledgeAlert(Long id) {
        SurveillanceAlertEntity entity = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with ID: " + id));
        if (!entity.isAcknowledged()) {
            entity.setAcknowledged(true);
            entity.setAcknowledgedAt(Instant.now());
            alertRepository.save(entity);
            log.info("Alert {} acknowledged", entity.getAlertId());
        }
        return AlertResponse.fromEntity(entity);
    }

    // ── Read queries ──────────────────────────────────────────────────────────

    public List<AlertResponse> findAll() {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> findByTraderId(String traderId) {
        return alertRepository.findByTraderIdOrderByCreatedAtDesc(traderId.trim()).stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> findBySymbol(String symbol) {
        return alertRepository.findBySymbolOrderByCreatedAtDesc(symbol.trim().toUpperCase()).stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> findByRuleName(String ruleName) {
        return alertRepository.findByRuleNameOrderByCreatedAtDesc(ruleName.trim().toUpperCase()).stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> findBySeverity(String severity) {
        return alertRepository.findBySeverityOrderByCreatedAtDesc(severity.trim().toUpperCase()).stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertResponse> findByTimeRange(Instant from, Instant to) {
        return alertRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to).stream()
                .map(AlertResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
