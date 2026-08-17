package com.finsight.service;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.model.Trade;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation managing trade ingestion and retrieval lifecycle.
 * Dependencies are injected exclusively via constructor for clear explicit dependencies and unit testability.
 */
@Service
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;

    public TradeServiceImpl(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
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
    public void deleteTrade(Long id) {
        if (!tradeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cannot delete. Trade record not found with ID: " + id);
        }
        tradeRepository.deleteById(id);
    }

    @Override
    public TradeResponse updateTradeStatus(Long id, TradeStatus status) {
        Trade existingTrade = tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cannot update status. Trade record not found with ID: " + id));

        existingTrade.setStatus(status);
        Trade updatedTrade = tradeRepository.save(existingTrade);
        return TradeResponse.fromEntity(updatedTrade);
    }
}
