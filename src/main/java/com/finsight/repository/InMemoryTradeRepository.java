package com.finsight.repository;

import com.finsight.model.Trade;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, lock-free in-memory repository implementation using ConcurrentHashMap and AtomicLong.
 * Designed to handle high-throughput concurrent access without global lock contention.
 */
@Repository
public class InMemoryTradeRepository implements TradeRepository {

    private final ConcurrentHashMap<Long, Trade> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0L);

    @Override
    public Trade save(Trade trade) {
        if (trade.getId() == null) {
            trade.setId(idSequence.incrementAndGet());
        }
        store.put(trade.getId(), trade);
        return trade;
    }

    @Override
    public Optional<Trade> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Trade> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) return false;
        return store.remove(id) != null;
    }

    @Override
    public boolean existsById(Long id) {
        if (id == null) return false;
        return store.containsKey(id);
    }

    /**
     * Clears all stored trades and resets the sequence generator. Used for test teardown.
     */
    public void clear() {
        store.clear();
        idSequence.set(0L);
    }
}
