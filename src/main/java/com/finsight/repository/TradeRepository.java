package com.finsight.repository;

import com.finsight.model.Trade;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface defining CRUD operations for Trade entities.
 * Abstraction layer to ensure Phase 2 PostgreSQL migration can be achieved seamlessly
 * by swapping the underlying Spring Data JPA repository implementation.
 */
public interface TradeRepository {

    /**
     * Saves a trade record. Generates an ID if trade.getId() is null.
     */
    Trade save(Trade trade);

    /**
     * Finds a trade by its unique identifier.
     */
    Optional<Trade> findById(Long id);

    /**
     * Retrieves all trades currently held in storage.
     */
    List<Trade> findAll();

    /**
     * Deletes a trade record by ID.
     * @return true if trade existed and was deleted, false otherwise.
     */
    boolean deleteById(Long id);

    /**
     * Checks whether a trade exists for a given ID.
     */
    boolean existsById(Long id);
}
