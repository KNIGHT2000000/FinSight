package com.finsight.service;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.model.Trade;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation managing trade ingestion, retrieval, and surveillance query lifecycle.
 * Utilizes Spring Data JPA repository for persistent database operations.
 * Dependencies are injected exclusively via constructor for clear explicit dependencies and unit testability.
 */
@Service
@Transactional(readOnly = true)
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;

    public TradeServiceImpl(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    @Transactional
    public TradeResponse createTrade(CreateTradeRequest request) {
        Trade trade = new Trade();
        trade.setSymbol(request.getSymbol().trim().toUpperCase());
        trade.setSide(request.getSide());
        trade.setQuantity(request.getQuantity());
        trade.setPrice(request.getPrice());
        trade.setTraderId(request.getTraderId().trim());
        trade.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : Instant.now());
        trade.setStatus(request.getStatus());

        Trade savedTrade = tradeRepository.save(trade);
        return TradeResponse.fromEntity(savedTrade);
    }

    @Override
    public TradeResponse getTradeById(Long id) {
        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade record not found with ID: " + id));
        return TradeResponse.fromEntity(trade);
    }

    @Override
    public List<TradeResponse> getAllTrades() {
        return tradeRepository.findAll()
                .stream()
                .map(TradeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TradeResponse updateTrade(Long id, UpdateTradeRequest request) {
        Trade existingTrade = tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update. Trade record not found with ID: " + id));

        existingTrade.setSymbol(request.getSymbol().trim().toUpperCase());
        existingTrade.setSide(request.getSide());
        existingTrade.setQuantity(request.getQuantity());
        existingTrade.setPrice(request.getPrice());
        existingTrade.setTraderId(request.getTraderId().trim());
        existingTrade.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : Instant.now());
        existingTrade.setStatus(request.getStatus());

        Trade updatedTrade = tradeRepository.save(existingTrade);
        return TradeResponse.fromEntity(updatedTrade);
    }

    @Override
    @Transactional
    public void deleteTrade(Long id) {
        if (!tradeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cannot delete. Trade record not found with ID: " + id);
        }
        tradeRepository.deleteById(id);
    }

    @Override
    @Transactional
    public TradeResponse updateTradeStatus(Long id, TradeStatus status) {
        Trade existingTrade = tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update status. Trade record not found with ID: " + id));

        existingTrade.setStatus(status);
        Trade updatedTrade = tradeRepository.save(existingTrade);
        return TradeResponse.fromEntity(updatedTrade);
    }

    @Override
    public List<TradeResponse> getTradesByTraderId(String traderId) {
        return tradeRepository.findByTraderId(traderId.trim())
                .stream()
                .map(TradeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeResponse> getTradesBySymbol(String symbol) {
        return tradeRepository.findBySymbol(symbol.trim().toUpperCase())
                .stream()
                .map(TradeResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
