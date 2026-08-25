package com.finsight.service;

import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.dto.UpdateTradeRequest;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.model.Trade;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private Trade sampleTrade;

    @BeforeEach
    void setUp() {
        sampleTrade = new Trade(
                1L,
                "AAPL",
                TradeSide.BUY,
                100L,
                new BigDecimal("180.50"),
                "TRADER_001",
                Instant.now(),
                TradeStatus.EXECUTED,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should create trade successfully with mapped fields")
    void createTrade_Success() {
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL", TradeSide.BUY, 100L, new BigDecimal("180.50"), "TRADER_001", null, TradeStatus.EXECUTED
        );

        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        TradeResponse response = tradeService.createTrade(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("AAPL", response.getSymbol());
        assertEquals(TradeSide.BUY, response.getSide());
        assertEquals(100L, response.getQuantity());
        assertEquals(new BigDecimal("180.50"), response.getPrice());
        assertEquals("TRADER_001", response.getTraderId());
        assertEquals(TradeStatus.EXECUTED, response.getStatus());

        verify(tradeRepository, times(1)).save(any(Trade.class));
    }

    @Test
    @DisplayName("Should retrieve trade by ID when found")
    void getTradeById_Success() {
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(sampleTrade));

        TradeResponse found = tradeService.getTradeById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
        assertEquals("AAPL", found.getSymbol());
        verify(tradeRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when querying non-existent trade")
    void getTradeById_NotFound() {
        when(tradeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tradeService.getTradeById(999L));
        verify(tradeRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should retrieve all trades")
    void getAllTrades_Success() {
        Trade trade2 = new Trade(2L, "MSFT", TradeSide.SELL, 200L, new BigDecimal("400.00"), "T2", Instant.now(), TradeStatus.PENDING);
        when(tradeRepository.findAll()).thenReturn(List.of(sampleTrade, trade2));

        List<TradeResponse> trades = tradeService.getAllTrades();

        assertEquals(2, trades.size());
        verify(tradeRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should update trade successfully")
    void updateTrade_Success() {
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(sampleTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTradeRequest updateRequest = new UpdateTradeRequest("AAPL", TradeSide.BUY, 150L, new BigDecimal("185.00"), "TRADER_001", Instant.now(), TradeStatus.EXECUTED);
        TradeResponse updated = tradeService.updateTrade(1L, updateRequest);

        assertEquals(150L, updated.getQuantity());
        assertEquals(new BigDecimal("185.00"), updated.getPrice());
        assertEquals(TradeStatus.EXECUTED, updated.getStatus());
        verify(tradeRepository, times(1)).save(sampleTrade);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException on update when trade does not exist")
    void updateTrade_NotFound() {
        when(tradeRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateTradeRequest updateRequest = new UpdateTradeRequest("AAPL", TradeSide.BUY, 150L, new BigDecimal("185.00"), "TRADER_001", Instant.now(), TradeStatus.EXECUTED);
        assertThrows(ResourceNotFoundException.class, () -> tradeService.updateTrade(999L, updateRequest));
    }

    @Test
    @DisplayName("Should delete trade successfully when ID exists")
    void deleteTrade_Success() {
        when(tradeRepository.existsById(1L)).thenReturn(true);
        doNothing().when(tradeRepository).deleteById(1L);

        tradeService.deleteTrade(1L);

        verify(tradeRepository, times(1)).existsById(1L);
        verify(tradeRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting non-existent trade")
    void deleteTrade_NotFound() {
        when(tradeRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> tradeService.deleteTrade(999L));
        verify(tradeRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Should update trade status successfully")
    void updateTradeStatus_Success() {
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(sampleTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeResponse response = tradeService.updateTradeStatus(1L, TradeStatus.CANCELLED);

        assertEquals(TradeStatus.CANCELLED, response.getStatus());
        verify(tradeRepository, times(1)).save(sampleTrade);
    }

    @Test
    @DisplayName("Should retrieve trades by traderId (Surveillance Query)")
    void getTradesByTraderId_Success() {
        when(tradeRepository.findByTraderId("TRADER_001")).thenReturn(List.of(sampleTrade));

        List<TradeResponse> trades = tradeService.getTradesByTraderId("TRADER_001");

        assertEquals(1, trades.size());
        assertEquals("TRADER_001", trades.get(0).getTraderId());
        verify(tradeRepository, times(1)).findByTraderId("TRADER_001");
    }

    @Test
    @DisplayName("Should retrieve trades by symbol (Surveillance Query)")
    void getTradesBySymbol_Success() {
        when(tradeRepository.findBySymbol("AAPL")).thenReturn(List.of(sampleTrade));

        List<TradeResponse> trades = tradeService.getTradesBySymbol("AAPL");

        assertEquals(1, trades.size());
        assertEquals("AAPL", trades.get(0).getSymbol());
        verify(tradeRepository, times(1)).findBySymbol("AAPL");
    }
}
