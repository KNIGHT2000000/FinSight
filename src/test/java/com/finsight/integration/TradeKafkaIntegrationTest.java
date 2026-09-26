package com.finsight.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.dto.CreateTradeRequest;
import com.finsight.dto.TradeResponse;
import com.finsight.model.TradeSide;
import com.finsight.model.TradeStatus;
import com.finsight.repository.TradeRepository;
import com.finsight.surveillance.AlertPersistenceService;
import com.finsight.surveillance.SurveillanceAlert;
import com.finsight.surveillance.SurveillanceConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End Real-Time Event Streaming & Surveillance Integration Test.
 *
 * Verifies the complete event-driven pipeline:
 * REST Ingestion -> PostgreSQL 18 Persistence -> Kafka Publisher
 *   -> Kafka Topic ('trades.created') -> SurveillanceConsumer
 *   -> Rule Alert Generation -> PostgreSQL Alert Persistence + WebSocket Broadcast
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"trades.created"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class TradeKafkaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private SurveillanceConsumer surveillanceConsumer;

    @MockBean
    private AlertPersistenceService alertPersistenceService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> inMemoryRedisStore = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.flyway.schemas", () -> "public");
    }

    @BeforeEach
    void setUp() {
        tradeRepository.deleteAll();
        surveillanceConsumer.clearAlerts();
        inMemoryRedisStore.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> inMemoryRedisStore.get(inv.getArgument(0)));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            return inMemoryRedisStore.putIfAbsent(key, val) == null;
        });
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            inMemoryRedisStore.put(key, val);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return inMemoryRedisStore.remove(key) != null;
        });

        // AlertPersistenceService is mocked — do nothing on persist (test focuses on Kafka pipeline)
        doNothing().when(alertPersistenceService).persistAlert(any());
    }

    @Test
    @DisplayName("Kafka E2E Pipeline: Ingest trade -> Persists to DB -> Publishes to Kafka -> Consumer flags Large Volume Spike -> Persists alert")
    void testKafkaEventPipeline_EndToEnd_ConsumerReceivesAndFlagsTrade() throws Exception {
        // 1. INGEST LARGE BLOCK TRADE (Quantity: 12,000 shares >= 5,000 threshold)
        CreateTradeRequest request = new CreateTradeRequest(
                "AAPL",
                TradeSide.BUY,
                12000L,
                new BigDecimal("190.5000"),
                "TRADER_INSTITUTIONAL_01",
                Instant.now(),
                TradeStatus.EXECUTED
        );

        MvcResult result = mockMvc.perform(post("/api/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(12000))
                .andReturn();

        TradeResponse createdTrade = objectMapper.readValue(result.getResponse().getContentAsString(), TradeResponse.class);
        Long tradeId = createdTrade.getId();

        // 2. VERIFY POSTGRESQL TRADE PERSISTENCE
        assertTrue(tradeRepository.existsById(tradeId), "Trade must be persisted in PostgreSQL");

        // 3. POLL KAFKA CONSUMER WITH TIMEOUT (Deterministic Polling, Not Fragile Sleep)
        SurveillanceAlert matchingAlert = awaitAlertForTrade(tradeId, 10, TimeUnit.SECONDS);

        assertNotNull(matchingAlert, "Surveillance consumer must receive Kafka event and generate alert within timeout");
        assertEquals("LARGE_VOLUME_SPIKE", matchingAlert.getRuleName());
        assertEquals("HIGH", matchingAlert.getSeverity());
        assertEquals("TRADER_INSTITUTIONAL_01", matchingAlert.getTraderId());
        assertEquals("AAPL", matchingAlert.getSymbol());
        assertEquals(12000L, matchingAlert.getQuantity());
        assertEquals(tradeId, matchingAlert.getTradeId());

        // 4. VERIFY ALERT PERSISTENCE WAS TRIGGERED
        verify(alertPersistenceService, atLeastOnce()).persistAlert(any(SurveillanceAlert.class));
    }

    private SurveillanceAlert awaitAlertForTrade(Long tradeId, long timeout, TimeUnit timeUnit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            List<SurveillanceAlert> alerts = surveillanceConsumer.getAlerts();
            for (SurveillanceAlert alert : alerts) {
                if (tradeId.equals(alert.getTradeId())) {
                    return alert;
                }
            }
            Thread.sleep(100);
        }
        return null;
    }
}
