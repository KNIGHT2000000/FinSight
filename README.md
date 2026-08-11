# FinSight — Real-Time Trade Surveillance Platform (Phase 1)

FinSight is a high-performance, real-time trade surveillance and market abuse detection platform inspired by enterprise compliance systems such as **NICE Actimize**. In global financial markets, trade surveillance platforms process high-throughput order and execution feeds to detect illegal activities including insider trading, wash trading, spoofing, and market manipulation under strict regulatory frameworks (e.g., SEC, FINRA, MiFID II). **FinSight Phase 1** establishes the core foundational building block: a low-latency, thread-safe in-memory CRUD REST API that ingests, validates, and manages structured trade records through a strict layered architecture.

---

## Architecture Overview

FinSight uses a decoupled, three-tier architecture ensuring complete separation between transport protocols, business rules, and storage abstractions:

```mermaid
graph TD
    Client[HTTP REST Client / Trading Gateway]
    
    subgraph Web Layer
        Controller[TradeController <br/> <i>@RestController</i>]
        Advice[GlobalExceptionHandler <br/> <i>@RestControllerAdvice</i>]
        DTO[Create / Update Trade DTOs <br/> <i>Bean Validation</i>]
    End

    subgraph Service Layer
        Service[TradeService Interface]
        ServiceImpl[TradeServiceImpl <br/> <i>Business Logic</i>]
    End

    subgraph Data Access Layer
        Repo[TradeRepository Interface]
        InMemRepo[InMemoryTradeRepository <br/> <i>ConcurrentHashMap + AtomicLong</i>]
    End

    Client -->|HTTP GET / POST / PUT / DELETE| Controller
    Controller -->|Validates DTO| DTO
    Controller -->|Delegates| Service
    Service -->|Implemented by| ServiceImpl
    ServiceImpl -->|Queries / Persists| Repo
    Repo -->|Implemented by| InMemRepo
    Advice -->|Formats Error JSON| Client
```

### Layer Interaction Sequence (ASCII)

```
[ HTTP Request ] 
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│  TradeController (@Valid DTO validation)                    │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Calls TradeService contract)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  TradeServiceImpl (Business Validation & Normalization)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Calls TradeRepository contract)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  InMemoryTradeRepository (Lock-free thread safety)           │
│  - ConcurrentHashMap<Long, Trade>                           │
│  - AtomicLong (CAS sequence generation)                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Technical Design Decisions & Trade-Offs

### 1. Lock-Free Concurrency vs. Synchronized Blocks
* **Decision**: Implemented `InMemoryTradeRepository` using `ConcurrentHashMap<Long, Trade>` for entity storage and `AtomicLong` for primary key generation.
* **Technical Justification**: High-frequency trade surveillance ingestion suffers severe throughput bottlenecks under traditional `synchronized` blocks or global locks due to thread contention. `ConcurrentHashMap` uses segment-level locking and lock-free read operations (CAS operations), while `AtomicLong.incrementAndGet()` guarantees unique sequence generation without locking. This ensures thread-safe concurrent creation (`POST`) and retrieval (`GET`) under heavy multi-threaded workloads.

### 2. Interface-Driven Decoupled Layering
* **Decision**: Maintained explicit interfaces (`TradeService`, `TradeRepository`) separate from their concrete implementations (`TradeServiceImpl`, `InMemoryTradeRepository`), even for Phase 1 in-memory storage.
* **Technical Justification**: Decoupling domain logic from persistence mechanisms enforces the **Dependency Inversion Principle (DIP)**. This ensures Phase 2's storage migration (e.g., swapping `InMemoryTradeRepository` for a Spring Data JPA / PostgreSQL repository) requires zero code changes to the controller or service business logic.

### 3. Strict Constructor Injection over Field Injection
* **Decision**: Enforced constructor injection across all components (`TradeController`, `TradeServiceImpl`), completely eliminating `@Autowired` on private fields.
* **Technical Justification**: Field injection hides dependencies, encourages monolithic class designs, and makes isolated unit testing difficult without starting a full Spring container context. Constructor injection guarantees dependency immutability (`final` fields), explicitly declares required collaborators, and enables fast, lightweight JUnit 5 unit tests without Spring context overhead.

### 4. API Boundary Guardrails via Jakarta Bean Validation
* **Decision**: Applied `@Valid` alongside Jakarta validation annotations (`@NotBlank`, `@Positive`, `@NotNull`) on incoming `CreateTradeRequest` and `UpdateTradeRequest` DTOs.
* **Technical Justification**: Garbage in results in invalid compliance alerts. Rejecting invalid trades (such as negative trade volumes, empty symbols, or missing trader IDs) at the controller boundary prevents corrupt data from contaminating downstream surveillance analytics pipelines.

### 5. Explicit HTTP Semantics & Uniform Error Contracts
* **Decision**: Returned `ResponseEntity<T>` from every controller handler method, adhering strictly to REST specifications (`201 Created` with `Location` header, `200 OK`, `204 No Content`, `400 Bad Request`, `404 Not Found`), backed by `@RestControllerAdvice`.
* **Technical Justification**: Exposing default Spring Boot stack traces exposes internal architecture and breaks client parsers. `@RestControllerAdvice` translates domain exceptions (`ResourceNotFoundException`, `MethodArgumentNotValidException`) into structured JSON payload contracts containing exact field-level validation errors and timestamps.

---

## REST API Specification

| HTTP Method | Endpoint | Description | Success Code | Error Codes |
|---|---|---|---|---|
| `POST` | `/api/v1/trades` | Ingest a new trade record | `201 Created` (with `Location` header) | `400 Bad Request` |
| `GET` | `/api/v1/trades/{id}` | Retrieve trade by ID | `200 OK` | `404 Not Found` |
| `GET` | `/api/v1/trades` | List all trade records | `200 OK` | — |
| `PUT` | `/api/v1/trades/{id}` | Update an existing trade record | `200 OK` | `400 Bad Request`, `404 Not Found` |
| `DELETE` | `/api/v1/trades/{id}` | Delete a trade record | `204 No Content` | `404 Not Found` |

---

## Domain Model & Trade Lifecycle Concept Mapping

```java
public class Trade {
    private Long id;           // Audit trail sequence ID
    private String symbol;     // Ticker / Financial Instrument (e.g., AAPL)
    private TradeSide side;    // BUY / SELL order direction
    private Long quantity;     // Trade size (spikes flag volume manipulation)
    private BigDecimal price;  // Unit price (out-of-band price flags off-market trades)
    private String traderId;   // Account ID for entity behavior aggregation
    private Instant timestamp; // High-precision execution timestamp for sequence reconstruction
    private TradeStatus status;// PENDING, EXECUTED, REJECTED execution state
}
```

---

## Building and Running Tests

### Prerequisites
* Java 17+
* Maven 3.8+

### Commands

```bash
# Compile and run all unit and concurrency tests
mvn clean test

# Run Spring Boot application locally
mvn spring-boot:run
```

---

## Multi-Phase Roadmap

> **Phase 1 Milestone**: Lock-free in-memory CRUD REST API with layered architecture, strict HTTP semantics, Bean Validation, and global exception handling.
>
> **Phase 2 Preview**: Integration of PostgreSQL for durable persistence, Redis for caching active trader risk profiles, and Spring Data JPA repository substitution without altering upstream service layers.
