# FinSight — Databricks Community Edition Setup Guide

## Scope and Honest Constraints

This is an **offline batch analytics job** running on **Databricks Community Edition** (free tier).

**What it is:**
- A PySpark notebook that reads manually-exported trade data from DBFS
- Computes trade volume aggregates, price volatility, and z-score anomaly detection
- Runs graph-based collusion ring detection using NetworkX + Louvain community detection
- Outputs two CSV summaries to DBFS

**What it is NOT:**
- A live pipeline connected to FinSight's PostgreSQL instance (Community Edition has no VPC/private networking to reach a local dev machine)
- A scheduled job (Community Edition does not support scheduled workflows)
- A production-grade batch pipeline (no SLAs, no monitoring, no retry logic)
- A Delta Lake or MLflow workflow (intentionally excluded to keep scope honest and reproducible)

This is the scope that Databricks Community Edition honestly supports. A production deployment would use Databricks Standard/Premium with Delta Lake, Unity Catalog, and Workflows — or AWS Glue / EMR for the same batch processing use case.

---

## Prerequisites

- A free Databricks Community Edition account: [community.cloud.databricks.com](https://community.cloud.databricks.com)
- The files from this `spark/` directory:
  - `sample_trades.csv` — sample dataset (or your own Postgres export)
  - `trade_analysis.py` — the analytics notebook

---

## Step 1 — Create a Cluster

1. Log into Databricks Community Edition
2. In the left sidebar, click **Compute**
3. Click **Create compute**
4. Configure:
   - **Cluster name:** `finsight-analytics`
   - **Cluster mode:** Single Node
   - **Databricks Runtime:** `14.3 LTS (Scala 2.12, Spark 3.5.0)` or newer
   - **Node type:** leave as default (Community Edition has one option)
5. Click **Create compute**
6. Wait for the cluster to start (~3–5 minutes, shows green circle)

---

## Step 2 — Upload Sample Data to DBFS

1. In the left sidebar, click **Catalog** (or **Data** on older UI versions)
2. Click **Browse DBFS** (top right or under Catalog)
3. Navigate to `/FileStore/` and create a new folder named `finsight`
4. Upload `sample_trades.csv` into `/FileStore/finsight/`

**Alternative — upload via notebook cell:**
```python
# Run this in a notebook cell to upload from the notebook UI
dbutils.fs.cp("file:/path/to/sample_trades.csv", "/FileStore/finsight/sample_trades.csv")
```

**Or use the Databricks CLI (if installed locally):**
```bash
databricks fs cp spark/sample_trades.csv dbfs:/FileStore/finsight/sample_trades.csv
```

**Verify the upload:**
```python
dbutils.fs.ls("/FileStore/finsight/")
```

---

## Step 3 — Import the Notebook

**Option A — Import as a Python file:**
1. In the left sidebar, click **Workspace**
2. Click the `⌄` dropdown next to your user folder → **Import**
3. Select **File** → choose `trade_analysis.py` from this repository
4. Click **Import**

**Option B — Create manually:**
1. Click **New** → **Notebook** in your workspace
2. Name it `finsight_trade_analysis`, language **Python**
3. Copy the contents of `trade_analysis.py` into the notebook cells
   - Each `# COMMAND ----------` marker separates cells in the `.py` format

---

## Step 4 — Attach Cluster and Install Libraries

The notebook's first cell runs `%pip install networkx python-louvain numpy pandas scipy`.

1. Open the notebook
2. In the top-right, click **Connect** → select `finsight-analytics`
3. Run **Cell 1** (the `%pip install` cell) first
4. When prompted "Restart Python?", click **Restart**
5. Then run the remaining cells in order

> **Note:** The `%pip install` must run before any import statements. The restart ensures the newly installed packages are available.

---

## Step 5 — Run the Notebook

Run cells in sequence using **Shift+Enter** or click **Run All** from the top menu.

**Expected output sequence:**
1. **Section 1:** Data load confirmation — "Loaded 60 trades"
2. **Section 2:** Trade volume per trader per day table
3. **Section 3:** Price volatility per symbol (TSLA should show highest volatility due to price swings)
4. **Section 4:** Z-score anomaly detection — TRADER_EPSILON's large TSLA trades (IDs 15, 38, 55) should flag
5. **Section 5:** Confirmation that anomaly CSV was saved to DBFS
6. **Section 6:** Graph construction → Connected components showing TRADER_ALPHA/BETA cluster and TRADER_GAMMA/DELTA cluster
7. **Louvain:** Community partitions confirming the two coordination rings
8. **Collusion ring output:** Both clusters flagged as "POTENTIAL_COLLUSION_RING — CANDIDATE FOR REVIEW"
9. **Section 7:** Confirmation that ring candidates CSV was saved to DBFS

---

## Step 6 — Download the Output Files

1. Go to **Catalog** → **Browse DBFS** → `/FileStore/finsight/output/`
2. You will see two directories:
   - `flagged_trades_anomaly/` — CSV parts of anomalous volume trades
   - `collusion_ring_candidates/` — CSV parts of flagged trader clusters

To download via URL (files under `/FileStore/` are directly downloadable):
```
https://community.cloud.databricks.com/files/finsight/output/flagged_trades_anomaly/part-00000.csv
```

---

## Exporting Your Own Data from PostgreSQL

To run this against real FinSight data, export the trades table from PostgreSQL:

```sql
-- Export to CSV (requires psql or pgAdmin)
\COPY (
    SELECT id, symbol, side, quantity, price, trader_id, timestamp, status, created_at
    FROM trades
    WHERE status = 'EXECUTED'
    ORDER BY timestamp
) TO '/tmp/finsight_trades.csv' WITH CSV HEADER;
```

Then upload the exported file to DBFS following Step 2 above, updating `TRADES_INPUT_PATH` in the Configuration cell.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `ModuleNotFoundError: No module named 'community'` | Re-run the `%pip install` cell and restart Python |
| `AnalysisException: Path does not exist` | Verify the CSV was uploaded to the exact path in `TRADES_INPUT_PATH` |
| Cluster shows "Terminated" | Start the cluster from Compute page (Community Edition auto-terminates after 2h) |
| `dbutils not defined` | You are running outside Databricks — this is expected for local testing |
| Empty collusion rings output | Lower `COORDINATION_WEIGHT_THRESHOLD` or check that time windows overlap in the data |
