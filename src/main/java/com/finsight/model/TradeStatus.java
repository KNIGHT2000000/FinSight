package com.finsight.model;

/**
 * Lifecycle status of a trade within trade surveillance pipelines:
 * - PENDING: Trade ingested into the surveillance system awaiting pre/post-trade validation or match processing.
 * - EXECUTED: Order matched and confirmed filled by the exchange/execution venue.
 * - REJECTED: Trade failed validation checks, compliance constraints, or exchange risk controls.
 */
public enum TradeStatus {
    PENDING,
    EXECUTED,
    REJECTED
}
