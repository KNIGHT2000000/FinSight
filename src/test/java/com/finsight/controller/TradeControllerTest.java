package com.finsight.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.service.IdempotencyService;
import com.finsight.service.TradeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("POST /api/v1/trades -> 201 Created with Location header (No Idempotency-Key)")
    void createTrade_Valid_Returns201CreatedAndLocationHeader() throws Exception {
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );
        TradeResponse mockResponse = new TradeResponse(
                1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );

        when(tradeService.createTrade(any(CreateTradeRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/trades/1")))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.side", is("BUY")))
                .andExpect(jsonPath("$.quantity", is(100)))
                .andExpect(jsonPath("$.price", is(175.50)))
                .andExpect(jsonPath("$.traderId", is("TRADER_01")))
                .andExpect(jsonPath("$.status", is("EXECUTED")));

        verify(tradeService, times(1)).createTrade(any(CreateTradeRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/trades with Idempotency-Key -> First request returns 201 Created and stores in Redis")
    void createTrade_WithNewIdempotencyKey_Returns201Created() throws Exception {
        String key = "key-test-123";
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );
        TradeResponse mockResponse = new TradeResponse(
                1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );

        when(idempotencyService.getCachedResponse(key)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(key)).thenReturn(true);
        when(tradeService.createTrade(any(CreateTradeRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/trades")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));

        verify(idempotencyService, times(1)).acquireLock(key);
        verify(idempotencyService, times(1)).storeResponse(eq(key), any(TradeResponse.class));
    }

    @Test
    @DisplayName("POST /api/v1/trades with Idempotency-Key -> Duplicate retry returns cached 200 OK without calling service")
    void createTrade_WithDuplicateIdempotencyKey_ReturnsCachedResponse() throws Exception {
        String key = "key-test-duplicate";
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );
        TradeResponse cachedResponse = new TradeResponse(
                1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("175.50"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );

        when(idempotencyService.getCachedResponse(key)).thenReturn(Optional.of(cachedResponse));

        mockMvc.perform(post("/api/v1/trades")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.symbol", is("AAPL")));

        verify(tradeService, never()).createTrade(any());
        verify(idempotencyService, never()).acquireLock(any());
    }

    @Test
    @DisplayName("POST /api/v1/trades -> 400 Bad Request when Bean Validation fails")
    void createTrade_Invalid_Returns400BadRequest() throws Exception {
        CreateTradeRequest request = new CreateTradeRequest(
                "", null, -50L, new BigDecimal("0.00"), "   ", Instant.now(), TradeStatus.EXECUTED
        );

        mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))));

        verifyNoInteractions(tradeService);
    }

    @Test
    @DisplayName("GET /api/v1/trades/{id} -> 200 OK")
    void getTradeById_Found_Returns200OK() throws Exception {
        TradeResponse mockResponse = new TradeResponse(
                10L, "MSFT", TradeSide.SELL, 500L, new BigDecimal("410.00"), "TRADER_02", Instant.now(), TradeStatus.EXECUTED
        );

        when(tradeService.getTradeById(10L)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/trades/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.symbol", is("MSFT")));
    }

    @Test
    @DisplayName("GET /api/v1/trades/{id} -> 404 Not Found")
    void getTradeById_NotFound_Returns404NotFound() throws Exception {
        when(tradeService.getTradeById(99L)).thenThrow(new ResourceNotFoundException("Trade record not found with ID: 99"));

        mockMvc.perform(get("/api/v1/trades/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Trade record not found with ID: 99")));
    }

    @Test
    @DisplayName("GET /api/v1/trades -> 200 OK")
    void getAllTrades_Returns200OK() throws Exception {
        List<TradeResponse> mockList = List.of(
                new TradeResponse(1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("170.00"), "T1", Instant.now(), TradeStatus.EXECUTED),
                new TradeResponse(2L, "NVDA", TradeSide.SELL, 50L, new BigDecimal("120.00"), "T2", Instant.now(), TradeStatus.PENDING)
        );

        when(tradeService.getAllTrades()).thenReturn(mockList);

        mockMvc.perform(get("/api/v1/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")))
                .andExpect(jsonPath("$[1].symbol", is("NVDA")));
    }

    @Test
    @DisplayName("GET /api/v1/trades/trader/{traderId} -> 200 OK")
    void getTradesByTraderId_Returns200OK() throws Exception {
        TradeResponse response = new TradeResponse(1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("170.00"), "TRADER_X", Instant.now(), TradeStatus.EXECUTED);
        when(tradeService.getTradesByTraderId("TRADER_X")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/trades/trader/TRADER_X"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].traderId", is("TRADER_X")));
    }

    @Test
    @DisplayName("GET /api/v1/trades/symbol/{symbol} -> 200 OK")
    void getTradesBySymbol_Returns200OK() throws Exception {
        TradeResponse response = new TradeResponse(1L, "AAPL", TradeSide.BUY, 100L, new BigDecimal("170.00"), "TRADER_X", Instant.now(), TradeStatus.EXECUTED);
        when(tradeService.getTradesBySymbol("AAPL")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/trades/symbol/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol", is("AAPL")));
    }

    @Test
    @DisplayName("PUT /api/v1/trades/{id} -> 200 OK")
    void updateTrade_Valid_Returns200OK() throws Exception {
        UpdateTradeRequest request = new UpdateTradeRequest(
                "AAPL", TradeSide.BUY, 200L, new BigDecimal("180.00"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );
        TradeResponse mockResponse = new TradeResponse(
                1L, "AAPL", TradeSide.BUY, 200L, new BigDecimal("180.00"), "TRADER_01", Instant.now(), TradeStatus.EXECUTED
        );

        when(tradeService.updateTrade(eq(1L), any(UpdateTradeRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/trades/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity", is(200)))
                .andExpect(jsonPath("$.price", is(180.00)));
    }

    @Test
    @DisplayName("DELETE /api/v1/trades/{id} -> 204 No Content")
    void deleteTrade_Found_Returns204NoContent() throws Exception {
        doNothing().when(tradeService).deleteTrade(1L);

        mockMvc.perform(delete("/api/v1/trades/1"))
                .andExpect(status().isNoContent());

        verify(tradeService, times(1)).deleteTrade(1L);
    }
}
