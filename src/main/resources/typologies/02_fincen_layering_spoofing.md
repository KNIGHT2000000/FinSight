# FinCEN Advisory: Layering, Spoofing, and Order Book Manipulation

**Source type:** Synthetic summary modelled on FinCEN (Financial Crimes Enforcement Network) advisory guidance  
**Pattern category:** Market Manipulation — Order Book Deception

---

## Pattern Description

Layering (also called spoofing) involves placing a large number of visible orders on one side of the order book with the intent to cancel them before execution. The goal is to create a false impression of supply or demand, causing genuine market participants to move their orders or execute against the manipulator's smaller, genuine orders on the opposite side.

**Spoofing** is a subset where a single large order is placed and rapidly cancelled. **Layering** refers to stacking multiple orders at different price levels to create a more convincing illusion of depth.

## Mechanics of the Scheme

1. Trader places 10–20 large SELL limit orders slightly above the current best ask, creating the appearance of significant sell-side pressure.
2. Genuine buyers, seeing this apparent supply, reduce their bid prices or cancel buy orders.
3. Trader executes a real BUY order at the now-depressed price.
4. Trader cancels all the fake SELL orders within milliseconds to seconds.
5. Price recovers. Trader now holds a long position at a below-market entry price.

## Observable Indicators

- High order cancellation rate: >80% of orders cancelled without execution within short windows (e.g., 500ms to 5 seconds).
- Large order placement immediately followed by cancellation coinciding with a price move.
- Directional asymmetry: large resting orders consistently on one side, executed trades consistently on the opposite side.
- Rapid oscillation between large visible order placement and full cancellation.
- Order sizes that are outliers relative to the trader's historical average filled quantity.

## Regulatory Framework

- **Dodd-Frank Act Section 747** (US): explicit anti-spoofing provision, codified in 7 USC §13(a)(5).
- **MiFID II Article 12** (EU): layering and spoofing as specific enumerated manipulation types.
- **CFTC Regulation** (US): enforcement actions against spoofing in futures and derivatives.

## Surveillance Rule Candidates

- Flag traders with order cancellation rate exceeding a configurable threshold (e.g., 75%) within a rolling 60-second window.
- Flag sequences where a large order on side X is placed and cancelled, and a smaller executed order on side Y occurs within the same window.
- Track the ratio of order quantity placed vs. order quantity executed per trader per session.
- Alert when a trader's resting order quantity exceeds 5× their average executed quantity in a symbol.
