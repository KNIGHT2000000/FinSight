package com.finsight.controller;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.model.TradeStatus;
import com.finsight.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST Controller exposing trade management endpoints.
 * Follows strict HTTP semantics and returns ResponseEntity<T> on every handler method.
 * Uses constructor injection for service dependency.
 */
@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    /**
     * Ingest a new trade record.
     * Returns 201 Created with a Location header pointing to the newly created resource.
     */
    @PostMapping
    public ResponseEntity<TradeResponse> createTrade(@Valid @RequestBody CreateTradeRequest request) {
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
     * Retrieve all trade records.
     * Returns 200 OK.
     */
    @GetMapping
    public ResponseEntity<List<TradeResponse>> getAllTrades() {
        List<TradeResponse> trades = tradeService.getAllTrades();
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
     * a simple patch endpoint to update the status of a trade
     * @param id the id of the trade to update
     * @param status the new status of the trade
     * @return the updated trade
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TradeResponse> updateTradeStatus(@PathVariable Long id, @RequestParam TradeStatus status) {
        TradeResponse updatedTrade = tradeService.updateTradeStatus(id, status);
        return ResponseEntity.ok(updatedTrade);
    }
}
