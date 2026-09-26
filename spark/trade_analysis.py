# Databricks notebook source
# FinSight — Batch Trade Analytics & Graph-Based Collusion Ring Detection
#
# SCOPE: Offline batch analysis of historical trade data exported from FinSight PostgreSQL.
# PLATFORM: Databricks Community Edition (free tier).
# NOTE: This is a batch/offline job. Community Edition does not support scheduled jobs
#       or live database connections. Data is read from DBFS after manual export from Postgres.
#
# This script conceptually mirrors the Kafka consumer's real-time rule-based flagging
# (LARGE_VOLUME_SPIKE, RAPID_ORDER_BURST) but as a heavier historical analysis across
# the full trade population, with graph-based collusion detection that single-trade
# rules structurally cannot catch.

# COMMAND ----------

# MAGIC %md
# MAGIC ## Step 0 — Install Dependencies
# MAGIC Run this cell first. Restart the Python interpreter when prompted.

# COMMAND ----------

# Install NetworkX (graph analytics) and python-louvain (community detection)
# These are not pre-installed on Databricks Community Edition clusters.
# python-louvain import name is `community`, not `python_louvain`.
%pip install networkx python-louvain numpy pandas scipy

# COMMAND ----------

# MAGIC %md
# MAGIC ## Configuration
# MAGIC Edit these constants to match your DBFS upload path and analysis thresholds.

# COMMAND ----------

# ── File paths ────────────────────────────────────────────────────────────────
# Upload sample_trades.csv via Databricks UI: Data → DBFS → Upload
TRADES_INPUT_PATH  = "/FileStore/finsight/sample_trades.csv"
OUTPUT_DIR         = "/FileStore/finsight/output"

# ── Anomaly detection thresholds ──────────────────────────────────────────────
# Z-score threshold: trades with quantity z-score above this are flagged.
# 1.5 = 1.5 standard deviations above the trader's historical mean quantity.
# Mirrors the LARGE_VOLUME_SPIKE rule in the Kafka surveillance consumer.
Z_SCORE_THRESHOLD = 1.5

# ── Collusion ring detection thresholds ───────────────────────────────────────
# Two traders form an edge if they trade the same symbol within TIME_WINDOW_MINUTES.
TIME_WINDOW_MINUTES = 60

# Minimum cluster size to examine (1 = isolated nodes are ignored automatically)
CLUSTER_SIZE_THRESHOLD = 2

# Minimum average number of shared trading windows for a cluster to be suspicious
COORDINATION_WEIGHT_THRESHOLD = 3

# Minimum graph density (0–1) for a cluster to be flagged
# 1.0 = fully connected (every pair has a direct coordination link)
COORDINATION_DENSITY_THRESHOLD = 0.5

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 1 — Load Trade Data from DBFS

# COMMAND ----------

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, avg, stddev, count, sum as spark_sum, min as spark_min,
    max as spark_max, round as spark_round, lit, when,
    date_trunc, to_timestamp
)
from pyspark.sql.types import StructType, StructField, LongType, StringType, DoubleType, TimestampType

# On Databricks, `spark` is available automatically.
# For local PySpark execution add: spark = SparkSession.builder.appName("FinSight").getOrCreate()

trades_schema = StructType([
    StructField("id",         LongType(),      nullable=False),
    StructField("symbol",     StringType(),     nullable=False),
    StructField("side",       StringType(),     nullable=False),
    StructField("quantity",   LongType(),       nullable=False),
    StructField("price",      DoubleType(),     nullable=False),
    StructField("trader_id",  StringType(),     nullable=False),
    StructField("timestamp",  StringType(),     nullable=True),
    StructField("status",     StringType(),     nullable=True),
    StructField("created_at", StringType(),     nullable=True),
])

raw_df = spark.read \
    .option("header", "true") \
    .schema(trades_schema) \
    .csv(TRADES_INPUT_PATH)

trades_df = raw_df \
    .withColumn("timestamp",  to_timestamp("timestamp")) \
    .withColumn("created_at", to_timestamp("created_at"))

print(f"Loaded {trades_df.count()} trades")
trades_df.printSchema()
trades_df.show(5, truncate=False)

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 2 — Trade Volume per Trader per Day

# COMMAND ----------

volume_by_trader_day = trades_df.groupBy(
    "trader_id",
    date_trunc("day", col("timestamp")).alias("trade_date")
).agg(
    spark_sum("quantity").alias("total_quantity"),
    spark_round(
        spark_sum(col("quantity").cast("double") * col("price")), 2
    ).alias("total_notional_usd"),
    count("*").alias("trade_count"),
    spark_round(avg("price"), 4).alias("avg_price")
).orderBy("trader_id", "trade_date")

print("=== Trade Volume per Trader per Day ===")
volume_by_trader_day.show(50, truncate=False)

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 3 — Price Volatility per Symbol

# COMMAND ----------

volatility_by_symbol = trades_df.groupBy("symbol").agg(
    spark_round(avg("price"),    4).alias("mean_price"),
    spark_round(stddev("price"), 4).alias("price_std_dev"),
    spark_round(
        stddev("price") / avg("price") * 100, 4
    ).alias("coefficient_of_variation_pct"),
    spark_round(spark_min("price"), 4).alias("min_price"),
    spark_round(spark_max("price"), 4).alias("max_price"),
    count("*").alias("trade_count")
).withColumn(
    "volatility_flag",
    when(col("coefficient_of_variation_pct") > 1.5, "HIGH_VOLATILITY").otherwise("NORMAL")
).orderBy(col("coefficient_of_variation_pct").desc())

print("=== Price Volatility per Symbol ===")
volatility_by_symbol.show(truncate=False)

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 4 — Anomaly Z-Score (Quantity Outlier Detection)
# MAGIC
# MAGIC For each trade, compute the z-score of its quantity relative to that
# MAGIC trader's historical mean and standard deviation across the full dataset.
# MAGIC Trades above the threshold are flagged as anomalous volume events.
# MAGIC
# MAGIC **Conceptual parallel to the Kafka consumer rule:**
# MAGIC The real-time `LARGE_VOLUME_SPIKE` rule triggers when a single trade
# MAGIC quantity ≥ 5,000 shares. This batch z-score analysis is trader-relative
# MAGIC and population-aware — it flags trades that are anomalous *for that
# MAGIC specific trader's behaviour*, catching sophisticated actors who trade
# MAGIC large quantities generally but occasionally go far beyond their norm.

# COMMAND ----------

# Compute per-trader statistics across the full historical window
trader_stats = trades_df.groupBy("trader_id").agg(
    avg("quantity").alias("mean_qty"),
    stddev("quantity").alias("std_qty"),
    count("*").alias("total_trades")
)

# Join stats back to trade level and compute z-score
trades_with_stats = trades_df.join(trader_stats, on="trader_id", how="left")

trades_scored = trades_with_stats.withColumn(
    "z_score",
    when(
        col("std_qty").isNotNull() & (col("std_qty") > 0),
        (col("quantity").cast("double") - col("mean_qty")) / col("std_qty")
    ).otherwise(lit(0.0))
).withColumn(
    "anomaly_flag",
    when(col("z_score") > Z_SCORE_THRESHOLD, "ANOMALOUS_VOLUME — CANDIDATE FOR REVIEW")
    .otherwise("NORMAL")
)

# Flagged trades only
anomalous_trades = trades_scored \
    .filter(col("z_score") > Z_SCORE_THRESHOLD) \
    .select(
        "id", "trader_id", "symbol", "side", "quantity", "price", "timestamp",
        spark_round("mean_qty",   2).alias("trader_mean_qty"),
        spark_round("std_qty",    2).alias("trader_std_qty"),
        spark_round("z_score",    4).alias("z_score"),
        "anomaly_flag"
    ).orderBy(col("z_score").desc())

print(f"=== Anomalous Trades (z-score > {Z_SCORE_THRESHOLD}) ===")
anomalous_trades.show(truncate=False)
flagged_count = anomalous_trades.count()
print(f"Total anomalous trades flagged: {flagged_count}")
print("NOTE: These are candidate findings for human analyst review — not definitive determinations.")

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 5 — Save Flagged Trade Anomalies

# COMMAND ----------

try:
    dbutils.fs.mkdirs(OUTPUT_DIR)
except NameError:
    # Not running on Databricks — skip dbutils call
    import os
    os.makedirs(OUTPUT_DIR.lstrip("/"), exist_ok=True)

anomalous_trades.write \
    .mode("overwrite") \
    .option("header", "true") \
    .csv(f"{OUTPUT_DIR}/flagged_trades_anomaly")

print(f"Flagged trade anomalies saved to: {OUTPUT_DIR}/flagged_trades_anomaly/")

# COMMAND ----------

# MAGIC %md
# MAGIC ## Section 6 — Graph-Based Collusion Ring Detection
# MAGIC
# MAGIC **What this detects:** Coordinated trading rings — groups of traders who
# MAGIC repeatedly trade the same symbols within the same time windows. This is a
# MAGIC proxy for potential coordination (informed trading, wash-trading rings, or
# MAGIC layered structures involving multiple apparently-independent accounts).
# MAGIC
# MAGIC **Why single-trade rules cannot catch this:** The Kafka consumer's rules
# MAGIC (LARGE_VOLUME_SPIKE, RAPID_ORDER_BURST) evaluate *individual* trades.
# MAGIC A collusion ring operating with moderate individual quantities — each
# MAGIC trade below any single-trade threshold — is structurally invisible to
# MAGIC those rules. Network analysis across the *relationship graph* of all
# MAGIC traders is required. This is consistent with how AML/fraud investigation
# MAGIC teams use network analysis in production (e.g., SWIFT network analytics,
# MAGIC NICE Actimize Entity Risk).
# MAGIC
# MAGIC **Graph construction:**
# MAGIC - Nodes = trader_id
# MAGIC - Edge between Trader A and Trader B if they both traded the same symbol
# MAGIC   within the same TIME_WINDOW_MINUTES window on any day
# MAGIC - Edge weight = number of such coincidences (higher = more coordination)

# COMMAND ----------

import networkx as nx
import pandas as pd
import numpy as np
from itertools import combinations

# Convert to Pandas for NetworkX — appropriate at this dataset scale.
# Spark GraphX would add complexity without benefit for O(100) node graphs.
trades_pd = trades_df.toPandas()
trades_pd["timestamp"] = pd.to_datetime(trades_pd["timestamp"], utc=True)
trades_pd["time_bucket"] = trades_pd["timestamp"].dt.floor(f"{TIME_WINDOW_MINUTES}min")

print(f"Building coordination graph from {len(trades_pd)} trades...")
print(f"Time window: {TIME_WINDOW_MINUTES} minutes")
print(f"Traders: {sorted(trades_pd['trader_id'].unique())}")
print(f"Symbols: {sorted(trades_pd['symbol'].unique())}")

# COMMAND ----------

# MAGIC %md
# MAGIC ### Build Coordination Edges

# COMMAND ----------

# For each (symbol, time_bucket), find all trader pairs who both appear.
# Each co-occurrence increments the edge weight between that pair.
edge_weights: dict[tuple, int] = {}

for (symbol, bucket), group in trades_pd.groupby(["symbol", "time_bucket"]):
    traders_in_window = group["trader_id"].unique().tolist()
    if len(traders_in_window) >= 2:
        for trader_a, trader_b in combinations(sorted(traders_in_window), 2):
            edge = (trader_a, trader_b)
            edge_weights[edge] = edge_weights.get(edge, 0) + 1

print("=== Coordination Edges (same symbol, same time window) ===")
if edge_weights:
    for (t1, t2), w in sorted(edge_weights.items(), key=lambda x: -x[1]):
        print(f"  {t1} <--> {t2} : {w} shared trading window(s)")
else:
    print("  No coordination edges detected.")

# Build the NetworkX graph
G = nx.Graph()
all_traders = trades_pd["trader_id"].unique()
G.add_nodes_from(all_traders)

for (trader_a, trader_b), weight in edge_weights.items():
    G.add_edge(trader_a, trader_b, weight=weight)

print(f"\nGraph: {G.number_of_nodes()} traders (nodes), {G.number_of_edges()} coordination edges")

# COMMAND ----------

# MAGIC %md
# MAGIC ### Connected Components Analysis

# COMMAND ----------

components = list(nx.connected_components(G))
print(f"=== Connected Components: {len(components)} cluster(s) ===\n")

for i, component in enumerate(sorted(components, key=len, reverse=True), 1):
    subgraph = G.subgraph(component)
    n_nodes  = len(component)
    density  = nx.density(subgraph)
    edges_in_comp = [(u, v, G[u][v]["weight"]) for u, v in subgraph.edges()]
    avg_weight = np.mean([w for _, _, w in edges_in_comp]) if edges_in_comp else 0.0

    print(f"Cluster {i}: {sorted(component)}")
    print(f"  Size:                  {n_nodes} traders")
    print(f"  Graph density:         {density:.3f}  (1.0 = fully connected)")
    print(f"  Avg coordination count:{avg_weight:.1f}  (shared windows per pair)")
    print()

# COMMAND ----------

# MAGIC %md
# MAGIC ### Louvain Community Detection
# MAGIC
# MAGIC Louvain identifies tightly-knit sub-communities within larger connected components.
# MAGIC Useful when a component has 5+ members and you need to find the densest core.

# COMMAND ----------

try:
    import community as community_louvain

    if G.number_of_edges() > 0:
        # random_state=42 for reproducibility
        partition = community_louvain.best_partition(G, weight="weight", random_state=42)
        community_groups: dict[int, list] = {}
        for trader, comm_id in partition.items():
            community_groups.setdefault(comm_id, []).append(trader)

        modularity = community_louvain.modularity(partition, G, weight="weight")

        print("=== Louvain Community Detection ===")
        for comm_id, members in sorted(community_groups.items()):
            print(f"  Louvain Community {comm_id}: {sorted(members)}")
        print(f"\n  Modularity: {modularity:.4f}  (higher = more distinct communities)")
    else:
        print("No edges — Louvain community detection skipped (no coordination detected).")
        partition = {}

except ImportError:
    print("python-louvain not installed. Run: %pip install python-louvain")
    partition = {}

# COMMAND ----------

# MAGIC %md
# MAGIC ### Flag Collusion Ring Candidates

# COMMAND ----------

ring_candidates = []

for i, component in enumerate(sorted(components, key=len, reverse=True), 1):
    n_nodes   = len(component)
    subgraph  = G.subgraph(component)
    density   = nx.density(subgraph)
    edges_in  = [(u, v, G[u][v]["weight"]) for u, v in subgraph.edges()]
    avg_w     = float(np.mean([w for _, _, w in edges_in])) if edges_in else 0.0
    total_w   = int(sum(w for _, _, w in edges_in))

    # Composite ring score: size × density × log(coordination_count + 1)
    # Using log to dampen extreme weight outliers.
    is_suspicious = (
        n_nodes   >= CLUSTER_SIZE_THRESHOLD and
        density   >= COORDINATION_DENSITY_THRESHOLD and
        avg_w     >= COORDINATION_WEIGHT_THRESHOLD
    )

    if is_suspicious:
        ring_score = round(n_nodes * density * np.log1p(avg_w), 3)
        flag = "POTENTIAL_COLLUSION_RING — CANDIDATE FOR REVIEW"
        note = (
            "This cluster repeatedly traded the same symbols within the same time windows. "
            "This is a proxy signal — NOT a definitive determination. "
            "Requires analyst review before any action is taken."
        )
    else:
        ring_score = 0.0
        flag = "NORMAL"
        note = "No suspicious coordination pattern exceeding configured thresholds."

    ring_candidates.append({
        "cluster_id":               i,
        "members":                  "|".join(sorted(component)),
        "cluster_size":             n_nodes,
        "graph_density":            round(density, 4),
        "avg_coordination_count":   round(avg_w, 2),
        "total_coordination_events": total_w,
        "ring_score":               ring_score,
        "flag":                     flag,
        "analyst_note":             note,
    })

ring_df = pd.DataFrame(ring_candidates)

print("=== Collusion Ring Detection Summary ===")
for _, row in ring_df.iterrows():
    print(f"\n  Cluster {int(row['cluster_id'])}: {row['members']}")
    print(f"    Size:           {int(row['cluster_size'])}")
    print(f"    Density:        {row['graph_density']}")
    print(f"    Avg coord:      {row['avg_coordination_count']}")
    print(f"    Ring score:     {row['ring_score']}")
    print(f"    Flag:           {row['flag']}")

flagged_rings = ring_df[ring_df["flag"] != "NORMAL"]
print(f"\n  {len(flagged_rings)} potential ring(s) flagged for analyst review.")

# COMMAND ----------

# MAGIC %md
# MAGIC ### Save Collusion Clusters Summary

# COMMAND ----------

ring_spark_df = spark.createDataFrame(ring_df)
ring_spark_df.write \
    .mode("overwrite") \
    .option("header", "true") \
    .csv(f"{OUTPUT_DIR}/collusion_ring_candidates")

print(f"Collusion ring candidates saved to: {OUTPUT_DIR}/collusion_ring_candidates/")

# COMMAND ----------

# MAGIC %md
# MAGIC ## Summary

# COMMAND ----------

total_trades = trades_pd.shape[0]
anomaly_count = anomalous_trades.count()
ring_count = len(flagged_rings)

print("=" * 65)
print("FINSIGHT — BATCH ANALYTICS RUN COMPLETE")
print("=" * 65)
print(f"  Total trades analysed:              {total_trades}")
print(f"  Anomalous volume trades flagged:    {anomaly_count}  (z-score > {Z_SCORE_THRESHOLD})")
print(f"  Potential collusion rings flagged:  {ring_count}")
print()
print("  Output files:")
print(f"    {OUTPUT_DIR}/flagged_trades_anomaly/")
print(f"    {OUTPUT_DIR}/collusion_ring_candidates/")
print()
print("  DISCLAIMER: All flags are CANDIDATE FINDINGS for human analyst")
print("  review. None constitute a definitive compliance determination.")
print("  No trades are modified, blocked, or auto-reported by this job.")
print("=" * 65)
