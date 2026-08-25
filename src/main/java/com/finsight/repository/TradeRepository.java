package com.finsight.repository;

import com.finsight.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA Repository interface for Trade entities.
 * 
 * Inherits standard CRUD and pagination operations from JpaRepository.
 * Defines domain-specific trade surveillance query methods aligned strictly
 * with pre-existing database indexes in V1 migration:
 * - idx_trades_symbol (symbol)
 * - idx_trades_trader_id (trader_id)
 * - idx_trades_trader_id_created_at (trader_id, created_at)
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /**
     * Retrieve all trades executed by a specific trader.
     * Uses index: idx_trades_trader_id
     */
    List<Trade> findByTraderId(String traderId);

    /**
     * Retrieve all trades executed for a specific instrument/symbol.
     * Uses index: idx_trades_symbol
     */
    List<Trade> findBySymbol(String symbol);

    /**
     * Retrieve trades for a trader within a specific audit time window.
     * Used in trade surveillance for velocity, wash trading, and spoofing pattern detection.
     * Uses composite index: idx_trades_trader_id_created_at
     */
    List<Trade> findByTraderIdAndCreatedAtBetween(String traderId, Instant start, Instant end);

    /**
     * Retrieve recent trades for a trader ordered chronologically descending.
     * Uses composite index: idx_trades_trader_id_created_at
     */
    List<Trade> findByTraderIdOrderByCreatedAtDesc(String traderId);
}
