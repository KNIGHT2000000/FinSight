# SEC Enforcement Summary: Front-Running and Trading Ahead

**Source type:** Synthetic summary modelled on SEC (U.S. Securities and Exchange Commission) enforcement action patterns  
**Pattern category:** Information Abuse — Trading Ahead of Material Non-Public Information

---

## Pattern Description

Front-running occurs when a broker, market maker, or other insider executes trades in a security for their own account (or a related account) with advance knowledge of a pending large customer order that will likely move the market price. Because the pending order has not yet been publicly disclosed, the front-runner has an information advantage that constitutes a form of securities fraud.

A related variant is **trading ahead**: a broker accepts a large customer order but executes their own proprietary position first, before routing the customer order, benefiting from the price impact the customer order creates.

## Mechanics of the Scheme

1. A broker receives a large institutional customer BUY order for Symbol X (e.g., 500,000 shares).
2. Before executing the customer order, the broker's proprietary desk purchases shares of Symbol X.
3. The customer order is then executed, driving the price up due to its size.
4. The proprietary desk sells at the higher post-order price, capturing the spread.
5. The customer receives fills at a worse price than they would have without front-running.

## Observable Indicators

- Proprietary trading desk executes in a symbol within a short window (e.g., 5 minutes) before a large customer institutional order in the same symbol and direction.
- Proprietary positions are consistently opened just before large customer-induced price moves.
- Pattern recurs across multiple institutional clients for the same proprietary trader.
- The timing gap between proprietary order and customer order is suspiciously consistent (suggesting systematic behaviour).
- Proprietary trade is fully reversed within the same session after the price impact materialises.

## Regulatory Framework

- **SEC Rule 10b-5** (US): trading on material non-public information.
- **FINRA Rule 5270** (US): front-running of block transactions — explicitly prohibits trading ahead of customer block orders.
- **MAR Article 8** (EU): insider dealing, including trading on information about pending orders.

## Surveillance Rule Candidates

- Flag instances where a proprietary trader and an institutional customer both trade the same symbol in the same direction within a configurable lead-time window (e.g., 0–300 seconds).
- Correlate proprietary position changes with subsequent large customer order arrival and price impact.
- Alert when proprietary pre-order profits (estimated as price delta × quantity) exceed a threshold on days with large client institutional flow.
- Track the time series correlation between proprietary buy signals and incoming institutional order flow per symbol.
