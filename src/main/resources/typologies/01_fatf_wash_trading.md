# FATF Typology: Wash Trading and False Market Activity

**Source type:** Synthetic summary modelled on FATF (Financial Action Task Force) typology guidance  
**Pattern category:** Market Manipulation — Self-dealing / Circular Trading

---

## Pattern Description

Wash trading involves a trader (or a network of accounts under common beneficial ownership) simultaneously or sequentially buying and selling the same financial instrument to create the appearance of market activity without any genuine change in beneficial ownership. The primary objectives are:

1. **Price inflation or deflation** — by setting artificial reference prices for the instrument.
2. **Volume inflation** — to attract genuine investors into an illiquid security by simulating active trading.
3. **Fee generation** — in rebate schemes where trading venues pay market makers per executed order, wash trading generates rebate payments with no market exposure.

## Observable Indicators

- The same account (or accounts under the same beneficial owner) appears on both sides of repeated trades for the same instrument within short intervals.
- Trade quantities and prices are near-identical on both sides.
- No net change in the trader's position after the wash cycle.
- Unusually high trade volume for an instrument relative to its available float.
- Trades clustered around illiquid opening or closing windows where price impact is greatest.
- Circular trading patterns across three or more accounts: Account A sells to B, B sells to C, C sells back to A.

## Regulatory Framework

Prohibited under:
- **SEC Rule 10b-5** (US): fraudulent devices in connection with securities transactions.
- **MiFID II Article 12** (EU): market manipulation including wash trades.
- **FINRA Rule 5210** (US): publication of transactions and quotations.

## Surveillance Rule Candidates

- Flag trades where the same trader ID appears on both the BUY and SELL side for the same symbol within a configurable time window.
- Flag instruments where one trader accounts for >30% of total daily volume.
- Flag net-zero position changes across same-day round-trip trades.
- Graph-based detection: identify cycles of length ≥ 3 in the trader interaction graph for the same symbol.
