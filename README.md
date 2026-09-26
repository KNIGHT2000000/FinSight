# FinSight — Real-Time Trade Surveillance & Market Abuse Detection Platform

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red)](https://redis.io/)
[![Databricks](https://img.shields.io/badge/Databricks-Community%20Edition-orange)](https://community.cloud.databricks.com/)
[![Ollama](https://img.shields.io/badge/Ollama-llama3.1:8b-purple)](https://ollama.com/)

---

## What is FinSight?

FinSight is a **real-time trade surveillance and market abuse detection platform** built in Spring Boot and Java. It demonstrates end-to-end distributed systems engineering for the financial compliance domain: from persistent trade lifecycle management through Kafka event-driven alerting, to offline batch analytics and a governed on-prem AI assistant for regulatory intelligence.

Built to the standard of a senior engineer portfolio project — defensible architecture, production-pattern code, honest scope claims, and thorough test coverage.

---

## Architecture

```
                        FinSight — MVP Architecture
                        ===========================

 ┌─────────────────────────────────────────────────────────────────────────────┐
 │  CLIENT LAYER                                                               │
 │  REST Client (curl/Postman)          WebSocket Client (browser/ws-cat)      │
 └────────────┬──────────────────────────────────────┬────────────────────────┘
              │ HTTP                                  │ WS
              ▼                                       ▼
 ┌────────────────────────────────────────────────────────────────────────────┐
 │  SPRING BOOT APPLICATION (port 8080)                                       │
 │                                                                            │
 │  ┌─────────────────────┐  ┌──────────────────────┐  ┌───────────────────┐ │
 │  │  TradeController    │  │  AssistantController │  │AlertWebSocketHandler│
 │  │  /api/v1/trades     │  │  /api/v1/assistant   │  │  /ws/alerts       │ │
 │  └────────┬────────────┘  └──────────┬───────────┘  └─────────┬─────────┘ │
 │           │                          │                         │           │
 │  ┌────────▼────────────┐  ┌──────────▼───────────┐            │           │
 │  │  TradeServiceImpl   │  │  AssistantService    │            │           │
 │  │  (idempotency gate) │  │  (RAG pipeline)      │            │           │
 │  └────────┬────────────┘  │  OllamaClient        │            │           │
 │           │               │  TypologyLoader      │            │           │
 │           │               │  InMemoryVectorStore │            │           │
 │           │               └──────────────────────┘            │           │
 │           │                                                    │           │
 │  ┌────────▼────────────────────────────────────────────────────▼─────────┐ │
 │  │  SurveillanceConsumer (Kafka @KafkaListener)                          │ │
 │  │  Rule engine: LARGE_VOLUME_SPIKE | RAPID_ORDER_BURST                  │ │
 │  │  ┌───────────────────────┐    ┌─────────────────────────────────────┐ │ │
 │  │  │ AlertPersistenceService│    │ AlertWebSocketHandler (broadcast)   │ │ │
 │  │  │ (JPA → Postgres)      │    │ CopyOnWriteArraySet<WebSocketSession>│ │ │
 │  │  └───────────────────────┘    └─────────────────────────────────────┘ │ │
 │  └───────────────────────────────────────────────────────────────────────┘ │
 └────────────┬──────────────────────────────────────────────────────────────┘
              │
   ┌──────────┼──────────────────────────────┐
   │          │                              │
   ▼          ▼                              ▼
┌──────┐  ┌───────┐  ┌──────────┐  ┌─────────────────────────┐
│Kafka │  │Postgres│  │  Redis   │  │  Ollama (localhost:11434)│
│topic │  │ 18     │  │(idempotency  │  llama3.1:8b            │
│trades│  │Flyway  │  │  gate)   │  │  nomic-embed-text        │
│.created  │V1+V2  │  │SET NX TTL│  └─────────────────────────┘
└──────┘  └───────┘  └──────────┘

   ┌─────────────────────────────────────────────────┐
   │  DATABRICKS COMMUNITY EDITION (offline batch)   │
   │  spark/trade_analysis.py                        │
   │                                                 │
   │  PySpark: volume • volatility • z-score anomaly │
   │  NetworkX + Louvain: collusion ring detection   │
   │                                                 │
   │  Input:  DBFS /FileStore/finsight/ (manual CSV) │
   │  Output: flagged_trades_anomaly/                │
   │          collusion_ring_candidates/             │
   └─────────────────────────────────────────────────┘
```

---

## Project Phases

### Phase 1 — In-Memory Trade CRUD (complete)
- Full REST API: `POST /api/v1/trades`, `GET`, `PUT`, `DELETE`, `PATCH /status`
- In-memory `ConcurrentHashMap` with `AtomicLong` ID generation
- Concurrency-tested with 100 simultaneous threads
- Bean Validation (`@Valid`) on all request bodies
- `GlobalExceptionHandler` — sanitized error responses (no stack trace leakage)

### Phase 2 — PostgreSQL, Flyway, Redis (complete)
- **PostgreSQL 18** via Testcontainers — Flyway V1/V2 migrations, `BIGSERIAL` PKs, `CHECK` constraints
- **Redis idempotency gate** — `SET NX EX` prevents duplicate trade submissions (distributed, TTL-based)
- Hikari connection pool (10 max, 5 idle, 30s leak detection)
- Full Testcontainers integration test suite with real Postgres + real Redis

### Phase 3 — Kafka Event Streaming + Alert Persistence (complete)
- `trades.created` Kafka topic, `@KafkaListener` consumer with idempotency envelope check
- Rule engine: `LARGE_VOLUME_SPIKE` (qty ≥ 5000) and `RAPID_ORDER_BURST` (5+ trades in 60s)
- `AlertPersistenceService` → `SurveillanceAlertRepository` → Postgres `surveillance_alerts` table (V2 migration)
- `AlertWebSocketHandler` — real-time alert broadcast to connected compliance clients
- Error-isolated dispatch: DB failure never blocks WebSocket alert delivery

### Section B — Spark / Databricks Batch Analytics (complete)
See **[Databricks Setup Guide](spark/DATABRICKS_SETUP.md)** and **[Notebook](spark/trade_analysis.py)**.

### Section C — Typology Intelligence Assistant / on-prem RAG (complete)
See **Section C** below.

---

## Section B — Spark / Databricks Batch Analytics

> **Scope:** Offline batch analysis of historically exported trade data. This is **not** a live pipeline and is **not** connected to the running PostgreSQL instance — Databricks Community Edition does not support private networking to a local dev machine. No scheduled jobs (not supported on Community Edition). Single script, single output.

### What it does

Running on **Databricks Community Edition** (free tier) against a CSV exported from FinSight's PostgreSQL:

| Analysis | Output |
|---|---|
| Trade volume per trader per day | Aggregated notional, count, average price |
| Price volatility per symbol | Std dev, coefficient of variation, volatility flag |
| Anomaly z-score detection | Flags trades where quantity is > 1.5σ above that trader's mean — mirrors the Kafka consumer's `LARGE_VOLUME_SPIKE` but trader-relative and population-aware |
| **Graph-based collusion ring detection** | NetworkX connected components + Louvain community detection over the trader interaction graph |

### Graph Collusion Detection — Real-World Parallel

The collusion ring detector builds a graph where nodes are `trader_id` and edges connect two traders when they trade the same symbol within the same hourly time window. It then runs:

1. **Connected components** (NetworkX) — finds all linked trader clusters
2. **Louvain community detection** (`python-louvain`) — identifies tightly-knit sub-communities within larger components
3. **Composite ring score** — size × density × log(avg\_coordination\_count)

This directly maps to how fraud investigation teams use **network analysis for AML/fraud rings** in production (NICE Actimize Entity Risk, SWIFT network analytics, FinCEN's use of graph analysis for beneficial ownership rings). It catches coordinated schemes that are structurally invisible to single-trade rules: a ring of 6 traders each placing 200-share trades breaches no individual threshold but appears as a dense clique in the coordination graph.

### Files

| File | Purpose |
|---|---|
| [`spark/sample_trades.csv`](spark/sample_trades.csv) | 60-row realistic dataset: 2 coordinated pairs + volume spiker |
| [`spark/trade_analysis.py`](spark/trade_analysis.py) | Databricks notebook (PySpark + NetworkX + Louvain) |
| [`spark/DATABRICKS_SETUP.md`](spark/DATABRICKS_SETUP.md) | Step-by-step Community Edition setup |

### Exporting from PostgreSQL

```sql
\COPY (
    SELECT id, symbol, side, quantity, price, trader_id, timestamp, status, created_at
    FROM trades WHERE status = 'EXECUTED' ORDER BY timestamp
) TO '/tmp/finsight_trades.csv' WITH CSV HEADER;
```

Upload to Databricks DBFS at `/FileStore/finsight/sample_trades.csv`.

---

## Section C — Typology Intelligence Assistant (on-prem RAG)

> **Scope:** A small, real RAG assistant over curated public regulatory typology summaries. It proposes candidate surveillance rules for human analyst review. It is **not** fine-tuned, **not** evaluated with RAGAS, **not** connected to the live rule engine, and **never** auto-applies any suggestion. Suggestions are text proposals only, explicitly labelled *unreviewed — requires analyst sign-off*.

### Why this exists

A senior engineer at NICE Actimize asked: *"How does this system evolve past 1400+ hardcoded rules?"* This assistant directly answers that question. Rather than manually reading FATF reports, FinCEN advisories, and SEC/FINRA enforcement summaries and transcribing them into rule tickets, a compliance analyst can query the assistant in natural language and receive a structured suggestion — clearly labelled as a proposal — that they can then review, refine, and submit to the rule governance process.

### Setup

```bash
# 1. Install Ollama
# https://ollama.com/download

# 2. Start the Ollama server
ollama serve

# 3. Pull the required models
ollama pull nomic-embed-text   # 137MB — fast embedding model (768 dims)
ollama pull llama3.1:8b        # ~4.7GB — instruction-following LLM

# 4. Start FinSight — the assistant indexes typologies at startup
./mvnw spring-boot:run
```

If Ollama is not running, all other FinSight endpoints work normally. The assistant endpoint returns `503 Service Unavailable` with setup instructions.

### Endpoint

```
POST /api/v1/assistant/query
Content-Type: application/json

{ "question": "What typologies involve coordinated trading across multiple accounts?" }
```

**Response:**
```json
{
  "answer": "Coordinated trading rings involve two or more apparently independent traders acting in concert...",
  "suggestedRule": "Flag clusters of ≥2 traders who repeatedly trade the same symbol within the same hourly window across ≥3 consecutive sessions, with graph density ≥0.5 [UNREVIEWED — REQUIRES ANALYST SIGN-OFF BEFORE ANY ACTION]",
  "retrievedSources": ["04_fatf_collusion_rings.md"],
  "disclaimer": "This response is generated by a small on-prem RAG assistant over curated regulatory typology summaries..."
}
```

### Typology Corpus (5 documents)

| File | Pattern |
|---|---|
| `01_fatf_wash_trading.md` | Wash trading / self-dealing / circular trading |
| `02_fincen_layering_spoofing.md` | Layering, spoofing, order book deception |
| `03_sec_front_running.md` | Front-running / trading ahead of block orders |
| `04_fatf_collusion_rings.md` | Coordinated trading rings / collusion networks |
| `05_finra_cross_market_manipulation.md` | Cross-market manipulation (equity-options, futures-to-equity) |

### RAG Pipeline

```
Query → nomic-embed-text (Ollama) → 768-dim float[]
      → InMemoryVectorStore cosine similarity search (top-3 chunks)
      → Augmented prompt with retrieved regulatory context
      → llama3.1:8b (Ollama) generate
      → Parse "SUGGESTED RULE:" line → label as unreviewed
      → AssistantResponse { answer, suggestedRule, retrievedSources, disclaimer }
```

No external vector database. The store is an `ArrayList<DocumentChunk>` with O(n·d) cosine similarity — appropriate for O(100) typology chunks. A production system would use pgvector or Qdrant.

### Governance Constraint (enforced at build time)

`AssistantService` has **no JPA repository dependencies** — verified by `AssistantGovernanceTest` using reflection. If a developer accidentally injects `TradeRepository` or any `JpaRepository` subtype into `AssistantService`, the test fails the build. This is a code-level guarantee, not just a README statement.

---

## Running Locally

### Prerequisites

- Java 17+
- Docker Desktop (for Postgres, Redis, Kafka via docker-compose)
- Optional: Ollama (for the RAG assistant)

### Start Infrastructure

```bash
docker-compose up -d
```

This starts PostgreSQL 18, Redis 7, and Kafka (with Zookeeper).

### Start Application

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup. Application is available at `http://localhost:8080`.

### Key Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/trades` | Submit a trade (idempotency-gated) |
| `GET` | `/api/v1/trades` | List all trades |
| `GET` | `/api/v1/trades/{id}` | Get single trade |
| `PUT` | `/api/v1/trades/{id}` | Full update |
| `PATCH` | `/api/v1/trades/{id}/status` | Update trade status |
| `DELETE` | `/api/v1/trades/{id}` | Delete trade |
| `POST` | `/api/v1/assistant/query` | Query the typology assistant |
| `WS` | `/ws/alerts` | Subscribe to real-time compliance alerts |

---

## Test Coverage

```
Unit Tests (no Docker):
  AssistantGovernanceTest   — 3 tests  (governance: no repo deps)
  AssistantServiceTest      — 9 tests  (cosine similarity, retrieval correctness)
  SurveillanceConsumerTest  — 6 tests  (rule engine, error isolation)
  TradeServiceImplTest      — 11 tests (CRUD, idempotency, concurrency)
  IdempotencyServiceTest    — 8 tests  (Redis gate)
  TradeControllerTest       — 11 tests (HTTP layer)

Integration Tests (Testcontainers — real Postgres + Redis):
  TradeIntegrationTest          — full CRUD lifecycle
  PostgresPersistenceIntegrationTest — Flyway migrations, V2 alert table, indices

Total: 48+ tests, 0 failures
```

Run all unit tests:
```bash
./mvnw test -Dtest="AssistantGovernanceTest,AssistantServiceTest,SurveillanceConsumerTest,TradeServiceImplTest,IdempotencyServiceTest,TradeControllerTest"
```

Run integration tests (requires Docker):
```bash
./mvnw test -Dtest="TradeIntegrationTest,PostgresPersistenceIntegrationTest"
```

---

## PR Descriptions

### Section B — Spark/Databricks Batch Analytics

**Summary:** Adds offline batch analytics module running on Databricks Community Edition.

**Changes:**
- `spark/sample_trades.csv` — 60-row realistic trade dataset (2 coordinated trader pairs for collusion detection, volume spiker for z-score anomaly)
- `spark/trade_analysis.py` — Databricks notebook with 4 analytics modules: trade volume per trader/day, price volatility per symbol, z-score anomaly detection (mirrors Kafka LARGE_VOLUME_SPIKE rule but trader-relative), and graph-based collusion ring detection (NetworkX connected components + Louvain)
- `spark/DATABRICKS_SETUP.md` — step-by-step setup guide, explicitly scoped to Community Edition limits

**Design decisions:**
- PySpark for the first three modules; NetworkX (via `toPandas()`) for graph analytics. At O(6) node, O(100) edge scale, NetworkX is the right tool — Spark GraphX adds complexity without benefit.
- `python-louvain` added for Louvain community detection — detects sub-communities within larger connected components.
- Graph edge weight = number of shared same-symbol hourly trading windows. Threshold-based flagging with configurable `CLUSTER_SIZE_THRESHOLD`, `COORDINATION_WEIGHT_THRESHOLD`, `COORDINATION_DENSITY_THRESHOLD`.
- All flags labelled "CANDIDATE FOR REVIEW" — no auto-reporting.
- Honest scope: README and setup guide explicitly state this is not a live pipeline, not scheduled, not Delta Lake/MLflow.

---

### Section C — Typology Intelligence Assistant (on-prem RAG)

**Summary:** Adds a governed on-prem RAG assistant answering the question "how does this system evolve past hardcoded rules?"

**New files:**
- `src/main/resources/typologies/0{1-5}_*.md` — 5 typology documents (wash trading, layering/spoofing, front-running, collusion rings, cross-market manipulation)
- `src/main/java/com/finsight/assistant/` — `OllamaClient`, `InMemoryVectorStore`, `TypologyLoader`, `AssistantService`, `AssistantController`, `DocumentChunk`, `AssistantRequest`, `AssistantResponse`
- `src/test/java/com/finsight/assistant/AssistantGovernanceTest.java` — reflection-based governance test
- `src/test/java/com/finsight/assistant/AssistantServiceTest.java` — hermetic retrieval unit tests

**Design decisions:**
- No Spring AI dependency — Spring's built-in `RestClient` (Spring Boot 3.2+) calls Ollama's REST API directly. Avoids version compatibility risk and is more educational.
- `InMemoryVectorStore` — simple `ArrayList<DocumentChunk>` + cosine similarity. O(n·d) retrieval is correct at 100-chunk scale.
- `nomic-embed-text` for embeddings (137MB, fast, high quality), `llama3.1:8b` for generation (runs on 16GB RAM dev hardware).
- `@PostConstruct` initialization — indexes typologies at startup, marks `ready=false` if Ollama unavailable. Controller returns 503.
- **Governance enforced at build time:** `AssistantGovernanceTest` uses reflection to assert `AssistantService` has no `JpaRepository` dependency. Fails build if violated. This is the technical guarantee that the assistant never has a write path.
- `AssistantServiceTest` — 9 hermetic tests with orthogonal unit-vector embeddings. Tests retrieval ordering, cosine similarity math, edge cases (zero vector, identical vectors, topK > store size).

---

## MVP Story (interview-ready paragraph)

FinSight is a real-time trade surveillance platform that demonstrates a complete, distributed financial systems stack. The core is a Spring Boot REST API with full trade lifecycle management, backed by PostgreSQL with Flyway migrations and a Redis idempotency gate to prevent duplicate submissions. An Apache Kafka consumer processes a `trades.created` event stream, evaluates each trade against configurable rule-based flagging logic — large volume spikes and rapid order bursts — and dispatches matched alerts concurrently to a PostgreSQL persistence layer and a live WebSocket channel for connected compliance dashboards. Layered onto this is a Databricks batch analytics module that operates on historically exported trade data, computing per-trader anomaly z-scores and running a NetworkX graph analysis to detect coordinated trading rings — the class of scheme that is structurally invisible to single-trade rules. Finally, a governed on-prem RAG assistant indexes curated regulatory typology documents (FATF, FinCEN, SEC/FINRA patterns) and answers natural language compliance questions with candidate surveillance rule suggestions, enforcing — at the code and build level, not just in documentation — that it has no write access to any system. The result is a platform that demonstrates real-time event-driven architecture, distributed idempotency, batch data engineering, and responsibly-scoped AI integration within a single coherent financial domain.

---

## Resume Bullets

### Section B — Spark / Databricks

- Built an offline batch analytics module on Databricks Community Edition using PySpark, computing trade volume aggregates, price volatility, and per-trader z-score anomaly detection over historical trade data exported from PostgreSQL
- Implemented graph-based collusion ring detection using NetworkX and Louvain community detection, identifying coordinated trader clusters that are structurally invisible to single-trade surveillance rules — an approach consistent with how network analysis is used in production AML/fraud systems (NICE Actimize, SWIFT)

### Section C — Typology Intelligence Assistant

- Designed and built an on-prem RAG assistant (Ollama + llama3.1:8b + nomic-embed-text) over curated regulatory typology documents (FATF/FinCEN/SEC/FINRA patterns) that proposes candidate surveillance rules in natural language — answering the question of how a rule-based system evolves beyond hardcoded logic
- Enforced AI governance constraints at the build level: reflection-based unit tests assert the assistant service has no JPA repository dependencies, guaranteeing no write path from the AI assistant to any persistent store or rule engine

### LinkedIn Update (draft)

> 🚀 Shipped two new capabilities in FinSight, my trade surveillance platform:
>
> **Batch Analytics (Databricks):** PySpark pipeline computing per-trader anomaly z-scores and graph-based collusion ring detection using NetworkX + Louvain — finding coordinated trading schemes that single-trade rules can't see.
>
> **Typology Intelligence Assistant:** On-prem RAG assistant (Ollama + llama3.1:8b) over curated FATF/FinCEN/SEC regulatory typology documents — proposes candidate surveillance rules in natural language. AI governance enforced at build time: the assistant literally cannot write to any database.
>
> Full stack: Spring Boot · PostgreSQL · Redis · Kafka · Databricks · Ollama · Testcontainers
>
> #FinTech #Compliance #TradeSurveillance #PySpark #RAG #MachineLearning #Java #DistributedSystems

---

## Technology Stack

| Layer | Technology |
|---|---|
| API Framework | Spring Boot 3.2.5 (Java 17) |
| Persistence | PostgreSQL 18, Spring Data JPA, Flyway |
| Caching / Idempotency | Redis 7 (SET NX EX) |
| Event Streaming | Apache Kafka, Spring Kafka |
| Real-Time Alerts | Spring WebSocket (raw WS, not STOMP) |
| Batch Analytics | PySpark, Databricks Community Edition |
| Graph Analytics | NetworkX, python-louvain (Louvain community detection) |
| LLM | Ollama (llama3.1:8b) — on-prem, no cloud API |
| Embeddings | Ollama (nomic-embed-text, 768-dim) |
| Vector Store | Custom InMemoryVectorStore (cosine similarity) |
| Testing | JUnit 5, Mockito, Testcontainers, Spring Boot Test, Spring Kafka Test |
| Build | Maven Wrapper (mvnw) |
