package com.finsight.service;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;

import java.util.List;

/**
 * Business logic contract for managing trade operations within FinSight.
 */
public interface TradeService {

    /**
     * Ingests a new trade record into the surveillance store.
     */
    TradeResponse createTrade(CreateTradeRequest request);

    /**
     * Retrieves a trade record by unique ID.
     * @throws com.finsight.exception.ResourceNotFoundException if trade does not exist.
     */
    TradeResponse getTradeById(Long id);

    /**
     * Retrieves all trade records in the system.
     */
    List<TradeResponse> getAllTrades();

    /**
     * Updates an existing trade record.
     * @throws com.finsight.exception.ResourceNotFoundException if trade does not exist.
     */
    TradeResponse updateTrade(Long id, UpdateTradeRequest request);

    /**
     * Deletes a trade record by ID.
     * @throws com.finsight.exception.ResourceNotFoundException if trade does not exist.
     */
    void deleteTrade(Long id);
}
