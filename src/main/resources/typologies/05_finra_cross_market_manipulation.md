# FINRA Enforcement Summary: Cross-Market Manipulation

**Source type:** Synthetic summary modelled on FINRA and CFTC enforcement action patterns  
**Pattern category:** Cross-Venue Manipulation — Using One Market to Profit in Another

---

## Pattern Description

Cross-market manipulation involves using activity in one financial market (e.g., equity options, futures, credit default swaps) to create artificial price conditions in a related market (e.g., the underlying equities cash market), or vice versa. The manipulator profits from a position held in Market B by artificially moving prices in Market A.

Because surveillance systems are typically siloed by instrument type or venue, cross-market schemes exploit the gaps between monitoring systems. A single surveillance engine watching only equities will not see the options positions that motivate the manipulative cash market trades.

## Common Cross-Market Schemes

### Scheme 1: Equity-to-Options Manipulation
1. Manipulator acquires a large long position in OTM (out-of-the-money) call options for Symbol X.
2. Manipulator then aggressively buys shares of Symbol X in the cash market, artificially inflating the price.
3. The options delta increases sharply; the call options are now ITM (in-the-money).
4. Manipulator sells the cash position and exercises or sells the options, capturing the spread.

### Scheme 2: Futures-to-Equity Manipulation ("Banging the Close")
1. Manipulator establishes a large long position in equity index futures near expiry.
2. Manipulator aggressively buys the constituent equities of that index in the last minutes of the trading session ("marking the close").
3. The artificial close prices inflate the settlement value of the index futures.
4. Manipulator profits on the futures position.

### Scheme 3: Short Selling with Credit Derivative Amplification
1. Manipulator acquires a large CDS (credit default swap) position on a company.
2. Manipulator then short-sells the company's equity and spreads negative news.
3. The equity decline triggers credit concerns, increasing the CDS value.
4. Manipulator profits on both legs.

## Observable Indicators

- Unusual concentration in OTM options for a specific symbol, followed by aggressive cash market buying in the same symbol.
- Large index futures positions that correlate with end-of-day price aggression in constituent equities.
- Abnormal short interest combined with concurrent CDS position increases.
- Cross-instrument position correlation that has no fundamental justification.
- Timing: options/futures position established before cash market manipulation begins.

## Regulatory Framework

- **SEC Rule 10b-5** and **Section 9(a)(2)** of the Securities Exchange Act (US): manipulation of security prices.
- **CFTC Regulation 180.1** (US): cross-market manipulation in commodity derivatives.
- **FINRA Rule 2010** (US): standards of commercial honour.
- **MAR Article 12** (EU): cross-market manipulation explicitly named.

## Surveillance Rule Candidates

- Cross-instrument correlation alert: flag when a trader holds large OTM options in Symbol X and simultaneously increases their cash position aggressively within a configurable window.
- End-of-session price aggression: flag when a trader's buy quantity as a percentage of total market volume exceeds a threshold in the last N minutes of session.
- Cross-asset position monitoring: join options holdings data with cash execution data per trader to compute net cross-instrument exposure anomalies.
- Short interest plus CDS alert: flag when the same beneficial owner increases short equity exposure and long CDS exposure in the same underlying within the same week.
- Futures settlement correlation: identify traders whose cash market activity in index constituents is statistically correlated with their futures settlement P&L over a rolling 90-day window.
