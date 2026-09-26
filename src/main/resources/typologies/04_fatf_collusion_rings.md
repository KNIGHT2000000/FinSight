# FATF Typology: Coordinated Trading Rings and Collusion Networks

**Source type:** Synthetic summary modelled on FATF Money Laundering through the Securities Sector typology report  
**Pattern category:** Coordinated Market Abuse — Multi-Party Collusion Networks

---

## Pattern Description

Coordinated trading rings involve two or more apparently independent traders or accounts acting in concert to manipulate prices, create artificial volume, or launder funds through the securities market. Unlike single-account wash trading, ring schemes are designed to appear as legitimate arm's-length transactions between independent counterparties, making them structurally harder to detect with single-trade surveillance rules.

The accounts in a collusion ring may be:
- Controlled by the same beneficial owner through nominee structures.
- Independently controlled but coordinated through encrypted messaging or pre-arranged signals.
- Operating as a "pump and dump" ring: buy coordinated across members, then sell to retail investors at inflated prices.

## Mechanics of the Scheme

1. **Ring formation:** 3–8 traders agree to coordinate (legally termed a "pool" or "ring").
2. **Position build-up:** Each member buys the target symbol on pre-agreed days and times. Individual trades appear normal in size — no single trade breaches surveillance thresholds.
3. **Price inflation:** Coordinated buying creates genuine price momentum that attracts external buyers.
4. **Exit:** Ring members sell their accumulated positions into external liquidity, distributing proceeds.
5. **Layering across accounts:** In money laundering variants, proceeds cycle through multiple accounts across jurisdictions, creating a complex audit trail.

## Observable Indicators

- Multiple traders consistently trading the same symbol within tight time windows across multiple sessions.
- Trading correlations between accounts that are too consistent to be coincidental market reactions.
- Accounts with otherwise unrelated trading histories showing sudden synchronisation on a specific symbol.
- Network analysis reveals dense clusters of traders sharing common trading windows, forming cliques in the trader interaction graph.
- Member accounts show correlated P&L that is inconsistent with independent trading decisions.
- Cross-account coordination: Account A buys, Account B buys, Account C sells all within minutes, then all three reverse — a coordinated cycle.

## Why Single-Trade Rules Cannot Detect Rings

A ring operating with 200-share trades per member, spread across 6 accounts, achieves 1,200-share aggregate exposure with no single trade exceeding common surveillance thresholds. The pattern is only visible at the **relationship graph level** — by analysing who trades with whom, in what symbols, and with what temporal correlation.

## Regulatory Framework

- **SEC Rule 10b-5** (US): coordinated schemes to defraud.
- **FINRA Rule 4511** (US): books and records obligations that support ring investigation.
- **FATF Recommendation 25** (international): transparency of legal persons and beneficial ownership.
- **MAR Article 12(1)(a)** (EU): transactions and orders that give false or misleading signals.

## Surveillance Rule Candidates

- Graph-based detection: build a trader interaction graph per symbol per day. Flag connected components with density ≥ threshold and minimum shared-window count ≥ threshold.
- Community detection (Louvain or Girvan-Newman): identify tightly-knit sub-communities within larger connected components.
- Alert when ≥ 3 traders consistently trade the same symbol within the same hourly window across ≥ 5 consecutive sessions.
- Cross-account P&L correlation analysis: flag account pairs whose daily P&L correlation coefficient exceeds 0.85 over a rolling 30-day window.
- Beneficial ownership linkage: enrich trader graph with beneficial ownership data to collapse ring members into a single node for aggregated position monitoring.
