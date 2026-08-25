package com.finsight.controller;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.model.TradeStatus;
import com.finsight.service.IdempotencyService;
import com.finsight.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller exposing trade management and surveillance endpoints.
 * Follows strict HTTP semantics and returns ResponseEntity<T> on every handler method.
 * Supports distributed request idempotency via the 'Idempotency-Key' HTTP header.
 */
@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeService tradeService;
    private final IdempotencyService idempotencyService;

    public TradeController(TradeService tradeService, IdempotencyService idempotencyService) {
        this.tradeService = tradeService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Ingest a new trade record.
     * Supports optional 'Idempotency-Key' header backed by Redis SETNX.
     * Returns 201 Created on fresh trade creation or cached 200/201 response on retry.
     */
    @PostMapping
    public ResponseEntity<TradeResponse> createTrade(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTradeRequest request) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // 1. Check if already processed and cached in Redis
            Optional<TradeResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(cached.get().getId())
                        .toUri();
                return ResponseEntity.ok(cached.get());
            }

            // 2. Attempt atomic lock acquisition (SETNX)
            boolean acquired = idempotencyService.acquireLock(idempotencyKey);
            if (!acquired) {
                // Key exists: check if another concurrent request just finished writing the response
                Optional<TradeResponse> completed = idempotencyService.getCachedResponse(idempotencyKey);
                if (completed.isPresent()) {
                    return ResponseEntity.ok(completed.get());
                }
                // Concurrent request is actively in-flight
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            try {
                TradeResponse createdTrade = tradeService.createTrade(request);
                idempotencyService.storeResponse(idempotencyKey, createdTrade);

                URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(createdTrade.getId())
                        .toUri();
                return ResponseEntity.created(location).body(createdTrade);
            } catch (Exception ex) {
                // Release lock on database / validation failure so client can retry
                idempotencyService.releaseLock(idempotencyKey);
                throw ex;
            }
        }

        // Standard execution when no Idempotency-Key is supplied
        TradeResponse createdTrade = tradeService.createTrade(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdTrade.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdTrade);
    }

    /**
     * Retrieve a specific trade by ID.
     * Returns 200 OK or 404 Not Found (via GlobalExceptionHandler).
     */
    @GetMapping("/{id}")
    public ResponseEntity<TradeResponse> getTradeById(@PathVariable Long id) {
        TradeResponse trade = tradeService.getTradeById(id);
        return ResponseEntity.ok(trade);
    }

    /**
     * Retrieve trade records with optional filtering by symbol or traderId.
     * Returns 200 OK.
     */
    @GetMapping
    public ResponseEntity<List<TradeResponse>> getTrades(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String traderId) {
        
        if (symbol != null && !symbol.isBlank()) {
            return ResponseEntity.ok(tradeService.getTradesBySymbol(symbol));
        }
        if (traderId != null && !traderId.isBlank()) {
            return ResponseEntity.ok(tradeService.getTradesByTraderId(traderId));
        }
        return ResponseEntity.ok(tradeService.getAllTrades());
    }

    /**
     * Surveillance query endpoint: retrieve all trades executed by a specific trader.
     */
    @GetMapping("/trader/{traderId}")
    public ResponseEntity<List<TradeResponse>> getTradesByTraderId(@PathVariable String traderId) {
        List<TradeResponse> trades = tradeService.getTradesByTraderId(traderId);
        return ResponseEntity.ok(trades);
    }

    /**
     * Surveillance query endpoint: retrieve all trades for a specific symbol/ticker.
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<TradeResponse>> getTradesBySymbol(@PathVariable String symbol) {
        List<TradeResponse> trades = tradeService.getTradesBySymbol(symbol);
        return ResponseEntity.ok(trades);
    }

    /**
     * Update an existing trade record.
     * Returns 200 OK or 404 Not Found.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TradeResponse> updateTrade(@PathVariable Long id, @Valid @RequestBody UpdateTradeRequest request) {
        TradeResponse updatedTrade = tradeService.updateTrade(id, request);
        return ResponseEntity.ok(updatedTrade);
    }

    /**
     * Delete a trade record by ID.
     * Returns 204 No Content or 404 Not Found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable Long id) {
        tradeService.deleteTrade(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Patch status of an existing trade record.
     * @param id the ID of the trade to update
     * @param status the new status of the trade
     * @return the updated trade
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TradeResponse> updateTradeStatus(@PathVariable Long id, @RequestParam TradeStatus status) {
        TradeResponse updatedTrade = tradeService.updateTradeStatus(id, status);
        return ResponseEntity.ok(updatedTrade);
    }
}
