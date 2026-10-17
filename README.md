# Uber Distributed Ride-Hailing Platform

[![Java 25](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Minikube-326CE5?style=flat-square&logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?style=flat-square&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![Neo4j](https://img.shields.io/badge/Neo4j-Graph-008CC1?style=flat-square&logo=neo4j&logoColor=white)](https://neo4j.com/)
[![Cassandra](https://img.shields.io/badge/Cassandra-Time--Series-1287B1?style=flat-square&logo=apachecassandra&logoColor=white)](https://cassandra.apache.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-9-005571?style=flat-square&logo=elasticsearch&logoColor=white)](https://www.elastic.co/)

A production-grade, polyglot distributed ride-hailing backend engineered with Spring Boot 4 and Java 25. The platform implements database-per-service isolation, an event-driven choreography Saga over RabbitMQ with automatic 5-hop compensating transactions, high-frequency GPS telemetry ingestion, graph-based collaborative matching, and cloud-native Kubernetes orchestration with dedicated observability pipelines.

---

## Architecture Navigation

- [System Architecture & Ingress](#system-architecture--ingress)
- [Microservice Boundaries & Responsibilities](#microservice-boundaries--responsibilities)
- [Distributed Saga & Event Choreography](#distributed-saga--event-choreography)
- [Ride Lifecycle State Machine](#ride-lifecycle-state-machine)
- [Polyglot Storage & Telemetry Pipelines](#polyglot-storage--telemetry-pipelines)
- [Cloud-Native Kubernetes Deployment](#cloud-native-kubernetes-deployment)
- [Enterprise Observability & Telemetry](#enterprise-observability--telemetry)
- [Design Patterns Implementation](#design-patterns-implementation)
- [Local Development & Quickstart](#local-development--quickstart)
- [Architectural Evolution & Roadmap](#architectural-evolution--roadmap)
- [Engineering Team & Attribution](#engineering-team--attribution)

---

## System Architecture & Ingress

The platform operates as a decentralized microservices mesh fronted by a reactive Spring Cloud Gateway. Ingress traffic undergoes non-blocking JWT authentication, claim extraction, and correlation injection before routing downstream. Domain services communicate synchronously via declarative OpenFeign clients for read-side queries, and asynchronously via RabbitMQ Topic Exchanges for state transitions and saga orchestration.

```mermaid
flowchart TD
    subgraph CLIENTS ["Client Ingress Layer"]
        RiderApp["Rider Application<br/>(Bookings & Payments)"]
        DriverApp["Driver Application<br/>(Telemetry & Dispatch)"]
        AdminPortal["Admin Console<br/>(Audit & Analytics)"]
    end

    subgraph INGRESS ["Edge & Security Boundary"]
        Gateway["Spring Cloud API Gateway :8080<br/>Reactive WebFlux • JJWT Claim Extraction • Route Resolution<br/>Header Injection: X-User-Id, X-User-Role, X-Correlation-ID"]
    end

    subgraph DOMAIN_SERVICES ["Domain Microservices Layer (Java 25 / Spring Boot 4)"]
        direction LR
        UserSvc["User Service<br/>:8081<br/>Auth & Profiles"]
        DriverSvc["Driver Service<br/>:8082<br/>Profiles & Ratings"]
        RideSvc["Ride Service<br/>:8083<br/>Trip Lifecycle & Surge"]
        LocSvc["Location Service<br/>:8084<br/>GPS Pings & Geofencing"]
        PaySvc["Payment Service<br/>:8085<br/>Billing & Refunds"]
    end

    subgraph BROKER ["Asynchronous Event Backbone (RabbitMQ)"]
        RabbitMQ["RabbitMQ Topic Exchanges & Dead Letter Queues<br/>user.events • driver.events • ride.events • location.events • payment.events"]
    end

    subgraph STORAGE_TIER ["Polyglot Persistence Layer"]
        subgraph RELATIONAL ["ACID Relational Core (PostgreSQL 17 — Database-per-Service)"]
            direction LR
            UserDB[("uberdb-users<br/>PostgreSQL")]
            DriverDB[("uberdb-drivers<br/>PostgreSQL")]
            RideDB[("uberdb-rides<br/>PostgreSQL")]
            LocDB[("uberdb-locations<br/>PostgreSQL")]
            PayDB[("uberdb-payments<br/>PostgreSQL")]
        end

        subgraph SPECIALIZED ["Specialized Data Engines"]
            direction LR
            RedisDB[("Redis<br/>L1/L2 Caching")]
            MongoDB[("MongoDB<br/>Audit Sourcing")]
            ESDB[("Elasticsearch 9<br/>Driver Search")]
            Neo4jDB[("Neo4j<br/>Trip Graph")]
            CassandraDB[("Apache Cassandra<br/>GPS Telemetry")]
        end
    end

    %% Client to Ingress
    RiderApp & DriverApp & AdminPortal --> Gateway

    %% Gateway to Services
    Gateway --> UserSvc
    Gateway --> DriverSvc
    Gateway --> RideSvc
    Gateway --> LocSvc
    Gateway --> PaySvc

    %% Service to Isolated Database
    UserSvc --> UserDB
    DriverSvc --> DriverDB
    RideSvc --> RideDB
    LocSvc --> LocDB
    PaySvc --> PayDB

    %% Inter-Service Communication
    UserSvc & DriverSvc & RideSvc & LocSvc & PaySvc <--> RabbitMQ

    %% Specialized persistence links
    DriverSvc -.-> ESDB
    RideSvc -.-> Neo4jDB
    LocSvc -.-> CassandraDB
    UserSvc & DriverSvc & RideSvc & LocSvc & PaySvc -.-> RedisDB
    UserSvc & DriverSvc & RideSvc & LocSvc & PaySvc -.-> MongoDB
```

### Core Architecture Highlights

1. **Database-per-Service Isolation**: Each microservice maintains complete encapsulation over its private PostgreSQL database. Direct cross-database joins and foreign keys are eliminated. Cross-service entity references are stored as plain IDs and resolved via Feign client contracts.
2. **Context Propagation & Defense in Depth**: The API Gateway verifies the JWT token, strips invalid requests, and forwards parsed claims downstream (`X-User-Id`, `X-User-Role`, and `X-Correlation-ID`). Internal services retain a servlet-based filter validating headers, ensuring zero-trust communication across cluster boundaries.
3. **Resilient Inter-Service RPC**: Inter-service read queries use OpenFeign interfaces centralized in the shared `contracts` module. Feign calls enforce strict request timeouts, error decoders, and circuit-safe degradation.

---

## Microservice Boundaries & Responsibilities

The system is decomposed into 5 autonomous domain services plus the edge gateway and shared contracts library:

| Service | Port | Database | Primary Responsibilities | Core Endpoints |
| :--- | :---: | :--- | :--- | :--- |
| **`api-gateway`** | `8080` | None (Stateless) | Ingress routing, reactive JWT verification, claim extraction, header propagation, correlation tracing. | `/api/auth/**`<br/>`/api/{users,drivers,rides,locations,payments}/**` |
| **`user-service`** | `8081` | `uberdb-users`<br/>MongoDB, Redis | User account registration, JWT credential issuance, role RBAC (`RIDER`, `DRIVER`, `ADMIN`), profile management. | `POST /api/auth/register`<br/>`POST /api/auth/login`<br/>`GET /api/users/{id}` |
| **`driver-service`** | `8082` | `uberdb-drivers`<br/>Elasticsearch, Mongo, Redis | Driver profiles, status lifecycle (`AVAILABLE`, `BUSY`, `OFFLINE`), document verification, rating aggregates. | `GET /api/drivers/search`<br/>`PUT /api/drivers/{id}/status`<br/>`GET /api/drivers/{id}/dashboard` |
| **`ride-service`** | `8083` | `uberdb-rides`<br/>Neo4j, Mongo, Redis | Ride creation, dispatch algorithms, surge pricing calculation, ride state machine, graph-based recommendations. | `POST /api/rides`<br/>`PUT /api/rides/{id}/complete`<br/>`PUT /api/rides/{id}/cancel` |
| **`location-service`** | `8084` | `uberdb-locations`<br/>Cassandra, Mongo, Redis | High-velocity GPS telemetry ingestion, driver proximity search, stationary driver anomaly detection. | `POST /api/locations/ping`<br/>`GET /api/locations/nearby`<br/>`GET /api/locations/driver/{id}/recent` |
| **`payment-service`** | `8085` | `uberdb-payments`<br/>MongoDB, Redis | Payment processing (Card, Wallet, Cash), coupon validation, dynamic refund strategy execution. | `POST /api/payments/ride/{id}`<br/>`POST /api/payments/refund`<br/>`GET /api/payments/ride/{id}` |
| **`contracts`** | N/A | None (Library) | Centralized Maven library defining shared event records, DTOs, and `@FeignClient` interfaces. | N/A |

---

## Distributed Saga & Event Choreography

When completing or cancelling rides, business transactions span multiple microservices. In the absence of distributed 2PC locking, the platform guarantees eventual consistency through an **Asynchronous Choreography Saga** with forward settlement and an automated 5-hop compensating rollback cascade.

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Driver App
    actor Rider as Rider App
    participant Gateway as API Gateway
    participant RideSvc as Ride Service (S3)
    participant Broker as RabbitMQ Topic Exchanges
    participant UserSvc as User Service (S1)
    participant DriverSvc as Driver Service (S2)
    participant LocSvc as Location Service (S4)
    participant PaySvc as Payment Service (S5)

    Note over Driver,RideSvc: Phase 1: Pre-Saga Validation & Trip Completion
    Driver->>Gateway: PUT /api/rides/{id}/complete
    Gateway->>RideSvc: Forward request with correlation headers
    par Pre-Saga Feign Pre-flight Checks
        RideSvc->>UserSvc: GET /api/users/{id} (Assert ACTIVE)
        UserSvc-->>RideSvc: 200 OK (User ACTIVE)
        RideSvc->>DriverSvc: GET /api/drivers/{id} (Assert BUSY)
        DriverSvc-->>RideSvc: 200 OK (Driver BUSY)
        RideSvc->>LocSvc: GET /api/locations/driver/{id}/recent (Assert ping < 5m)
        LocSvc-->>RideSvc: 200 OK (Recent GPS Ping Valid)
    end
    RideSvc->>RideSvc: Status -> COMPLETED, compute surge fare
    RideSvc->>Broker: Publish ride.completed {rideId, userId, driverId, fare}

    Note over Broker,PaySvc: Phase 2: Asynchronous Fan-Out & Settlement Initialization
    par Event Distribution
        Broker->>UserSvc: Consume ride.completed -> Update rider statistics
        Broker->>DriverSvc: Consume ride.completed -> Set status AVAILABLE, credit earnings
        Broker->>LocSvc: Consume ride.completed -> Stamp terminal location
        Broker->>PaySvc: Consume ride.completed -> Create PENDING payment
    end
    PaySvc->>Broker: Publish payment.initiated {rideId, paymentId}
    Broker->>RideSvc: Consume payment.initiated -> Status -> PAYMENT_PENDING

    Note over Rider,PaySvc: Phase 3: Payment Execution (Two Execution Branches)
    Rider->>Gateway: POST /api/payments/ride/{id}
    Gateway->>PaySvc: Process payment (Credit Card / Wallet)

    alt Happy Path: Settlement Successful
        PaySvc->>Broker: Publish payment.completed {rideId, paymentId}
        Broker->>RideSvc: Consume payment.completed -> Status -> PAID
        Note over RideSvc: Transaction successfully finalized
    else Unhappy Path: Settlement Failure & Compensation Cascade
        PaySvc->>Broker: Publish payment.failed {rideId, reason}
        Broker->>RideSvc: Consume payment.failed -> Status -> PAYMENT_FAILED
        RideSvc->>Broker: Publish ride.cancelled {rideId, reason: "payment_failed"}
        par Compensation Cascade (5-Hop Rollback)
            Broker->>UserSvc: Consume ride.cancelled -> Rollback rider statistics
            Broker->>DriverSvc: Consume ride.cancelled -> Deduct earnings, reset status
            Broker->>LocSvc: Consume ride.cancelled -> Log cancellation telemetry
            Broker->>PaySvc: Consume ride.cancelled -> Execute refund strategy
        end
        PaySvc->>Broker: Publish payment.refunded {rideId, paymentId}
        Broker->>RideSvc: Consume payment.refunded -> Status -> REFUNDED
        Note over RideSvc: Distributed state restored to consistent baseline
    end
```

### Saga Failure Handling & Compensation Guarantees

1. **Pre-Saga Validation Gate**: Before publishing `ride.completed`, `ride-service` executes 3 synchronous Feign pre-checks against `user-service`, `driver-service`, and `location-service`. If the passenger is deactivated, the driver is not currently `BUSY`, or the driver's latest GPS ping is older than 5 minutes, the operation halts with HTTP 400. No events are emitted, avoiding false saga triggers.
2. **Eventual Consistency & DLQs**: RabbitMQ consumers are configured with dedicated Dead Letter Queues (`*.dlq`) and exponential backoff retry. Poison messages that cannot be processed are routed to DLQs without stalling the message bus.
3. **Idempotent Consumers**: Every consumer verifies whether the local entity state already reflects the event before applying mutations. Duplicate deliveries caused by network retries result in safe no-ops.

---

## Ride Lifecycle State Machine

Ride transitions are governed by a deterministic Finite State Machine (FSM) enforcing valid progression across both synchronous requests and asynchronous saga events:

```mermaid
stateDiagram-v2
    [*] --> REQUESTED: Rider requests booking
    REQUESTED --> ACCEPTED: Driver accepts dispatch
    REQUESTED --> CANCELLED: Rider cancels prior to assignment
    
    ACCEPTED --> IN_PROGRESS: Driver arrives and starts trip
    ACCEPTED --> CANCELLED: Rider or driver cancels
    
    IN_PROGRESS --> COMPLETED: Driver marks trip completed<br/>(Pre-checks passed)
    
    COMPLETED --> PAYMENT_PENDING: S3 consumes payment.initiated
    
    PAYMENT_PENDING --> PAID: Payment transaction approved (Saga finished)
    PAYMENT_PENDING --> PAYMENT_FAILED: Payment declined / gateway failure
    
    PAYMENT_FAILED --> REFUNDED: Compensation cascade finishes
    
    PAID --> [*]
    REFUNDED --> [*]
    CANCELLED --> [*]
```

---

## Polyglot Storage & Telemetry Pipelines

Ride-hailing workloads exhibit divergent throughput, query latency, and data structural requirements. Rather than forcing all access patterns into a single relational schema, the platform routes distinct data streams to 6 specialized storage engines:

```mermaid
flowchart LR
    subgraph PATTERNS ["Data Access Patterns"]
        P1["ACID Core Transactions<br/>(Users, Rides, Ledgers)"]
        P2["High-Frequency GPS Stream<br/>(Real-Time Telemetry)"]
        P3["Fuzzy Text & Geospatial Filter<br/>(Driver Search & Catalog)"]
        P4["Social & Co-Travel Graph<br/>(Collaborative Recommendations)"]
        P5["Sub-millisecond Read Cache<br/>(Driver Profiles & Dashboards)"]
        P6["Immutable Audit Log<br/>(Compliance & Event History)"]
    end

    subgraph ENGINES ["Selected Storage Engines"]
        E1[("PostgreSQL 17<br/>5 Isolated Instances")]
        E2[("Apache Cassandra<br/>Wide-Column Time-Series")]
        E3[("Elasticsearch 9<br/>Inverted Index Engine")]
        E4[("Neo4j<br/>LPG Property Graph")]
        E5[("Redis 7<br/>In-Memory Key-Value")]
        E6[("MongoDB<br/>BSON Document Store")]
    end

    subgraph ADVANTAGES ["Architectural Rationale & Guarantees"]
        A1["Strict relational integrity, row-level locking, foreign keys within bounded contexts."]
        A2["LSM-tree append performance, linear write scaling, TTL auto-eviction for GPS pings."]
        A3["N-gram tokenization, typo tolerance, multi-criteria filtering across driver attributes."]
        A4["Index-free adjacency, native Cypher graph traversal for passenger-driver matching."]
        A5["In-memory read latencies (<2ms), LRU memory management, database offloading."]
        A6["Schemaless event storage, append-only domain auditing, JSON document flexibility."]
    end

    P1 --> E1 --> A1
    P2 --> E2 --> A2
    P3 --> E3 --> A3
    P4 --> E4 --> A4
    P5 --> E5 --> A5
    P6 --> E6 --> A6
```

### Storage Engine Technical Matrix

| Engine | Service Owner | Primary Data Entity | Access Pattern | Architectural Justification |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL 17** | All Services | Users, Drivers, Rides, Locations, Payments | Strong ACID transactions, row-level concurrency, indexed lookups | Guarantees transactional isolation for financial and lifecycle state. Split into 5 standalone instances to enforce strict domain boundaries. |
| **Apache Cassandra** | `location-service` | `driver_locations` | High-write time-series GPS coordinates (`driver_id`, `timestamp`) | Handles continuous write-heavy telemetry streams without read-write contention. Partitioned by `driver_id` for instant temporal retrieval. |
| **Elasticsearch 9** | `driver-service` | `drivers` index | Fuzzy name search, license plate indexing, vehicle classification | Powers search-as-you-type and fuzzy multi-attribute queries that would degrade relational database performance. |
| **Neo4j** | `ride-service` | `(:UserNode)-[:RODE_WITH]->(:DriverNode)` | Graph traversals, co-occurrence path queries, collaborative filtering | Computes personalized driver recommendations using Cypher graph traversal based on shared ride history and ratings. |
| **Redis 7** | All Services | Cache keys (`driver:profile:*`, `ride:status:*`, `dashboard:*`) | Key-value lookups with TTL expiration and LRU eviction policy | Protects relational backends from high-frequency read spikes for frequently requested driver dashboards and ride states. |
| **MongoDB** | All Services | `auth_events`, `driver_events`, `ride_events`, `payment_audit_trail` | Append-only event ingestion, document querying | Stores immutable domain event streams captured by Observer listeners for regulatory compliance and audit trails. |

---

## Cloud-Native Kubernetes Deployment

The production architecture deploys to Kubernetes (Minikube-ready) structured across two physically separated namespaces to isolate platform workloads from telemetry infrastructure:

- **`uber` Namespace**: Hosts the 5 Spring Boot application deployments, API Gateway, 5 PostgreSQL StatefulSets, Cassandra, Neo4j, Elasticsearch, Redis, MongoDB, and RabbitMQ.
- **`monitoring` Namespace**: Hosts the telemetry stack (Loki StatefulSet, Prometheus Deployment, Grafana Deployment).

```mermaid
flowchart TD
    subgraph INGRESS_K8S ["Ingress (NodePort: 30080)"]
        K8sGateway["API Gateway Deployment<br/>ClusterIP :8080 • NodePort :30080"]
    end

    subgraph NS_UBER ["Namespace: uber"]
        subgraph APP_PODS ["Spring Boot Services (Deployments — Limits: 768Mi)"]
            PodUser["user-service"]
            PodDriver["driver-service"]
            PodRide["ride-service"]
            PodLoc["location-service"]
            PodPay["payment-service"]
        end

        subgraph STATEFUL_STORES ["StatefulSets with PVCs (Limits: 256Mi - 768Mi)"]
            PGs["5x PostgreSQL StatefulSets<br/>(user, driver, ride, loc, pay)"]
            NoSQLs["Cassandra • Neo4j • Elasticsearch<br/>MongoDB • Redis • RabbitMQ"]
        end
    end

    subgraph NS_MONITORING ["Namespace: monitoring (NodePort: 30030)"]
        Prom["Prometheus v2.51<br/>Scrapes /actuator/prometheus every 15s"]
        Loki["Grafana Loki v2.9<br/>Ingests structured logs from Loki4j"]
        Grafana["Grafana v10.4<br/>5 Service Dashboards (LogQL & PromQL)"]
    end

    %% External traffic
    K8sGateway --> PodUser & PodDriver & PodRide & PodLoc & PodPay

    %% Pods to Storage
    PodUser & PodDriver & PodRide & PodLoc & PodPay --> PGs & NoSQLs

    %% Observability flows
    PodUser & PodDriver & PodRide & PodLoc & PodPay -. "HTTP Push (Loki4j)" .-> Loki
    Prom -. "HTTP Scrape (/prometheus)" .-> PodUser & PodDriver & PodRide & PodLoc & PodPay
    Loki & Prom --> Grafana
```

### Kubernetes Manifest Architecture

```
k8s/
├── namespaces/
│   ├── namespace.yaml                    # Application namespace: uber
│   └── monitoring-namespace.yaml         # Observability namespace: monitoring
├── secrets/                              # Base64 credentials & JWT secrets
├── configmaps/                           # Per-service environment configuration
├── pvcs/                                 # Persistent volume claims for databases
├── statefulsets/                         # Stateful workloads (PostgreSQL, NoSQL, Broker)
├── deployments/                          # Application microservice workloads
├── services/                             # ClusterIP & Headless internal services
├── api-gateway/                          # Gateway Deployment & NodePort Service (30080)
├── monitoring/                           # Loki, Prometheus, and Grafana manifests
└── deploy-all.sh                         # Automated deployment orchestration script
```

---

## Enterprise Observability & Telemetry

Observability is embedded directly into the runtime through distributed tracing headers, structured JSON log streaming, and time-series metric collection:

```mermaid
flowchart LR
    subgraph INGRESS_TRACE ["Ingress Request"]
        REQ["Incoming Client Request"] -->|Injects X-Correlation-ID| GW["API Gateway"]
    end

    subgraph CONTEXT_PROPAGATION ["Distributed Context Propagation"]
        GW -->|HTTP Header: X-Correlation-ID| FEIGN["OpenFeign Clients"]
        GW -->|AMQP Header: correlationId| RABBIT["RabbitMQ Events"]
        FEIGN & RABBIT --> MDC["MDC Context Injection<br/>(correlationId, rideId, userId, driverId)"]
    end

    subgraph TELEMETRY_PIPELINE ["Telemetry Ingestion & Dashboards"]
        MDC -->|Push JSON Streams| LOKI["Loki4j Appender → Grafana Loki"]
        MDC -->|Pull /actuator/prometheus| PROM["Prometheus Scraper"]
        LOKI & PROM --> DASH["Grafana Service Dashboards<br/>LogQL Spike & PromQL Latency Panels"]
    end
```

1. **MDC Correlation Tracing**: An `X-Correlation-ID` header is attached at the API Gateway and propagated across HTTP Feign calls and RabbitMQ message headers. Each service's servlet filter and AMQP listeners inject the correlation ID alongside domain entity IDs (`userId`, `driverId`, `rideId`, `paymentId`, `routingKey`) into SLF4J MDC.
2. **Structured Logging via Loki4j**: Log events are formatted as JSON and shipped asynchronously to Grafana Loki via the `loki-logback-appender`. LogQL queries allow real-time tracing of an individual ride lifecycle across all 5 microservices using its correlation ID.
3. **Prometheus Metrics & Service Dashboards**: Spring Boot Actuator exposes Micrometer metrics scraped every 15 seconds. Each service includes a pre-configured Grafana dashboard featuring:
   - **PromQL Panels**: HTTP request throughput, P50/P95/P99 latency percentiles, JVM heap & GC pressure, HikariCP connection pool saturation.
   - **LogQL Panels**: Real-time error rate detection, correlation ID transaction traces, RabbitMQ publish/consume velocity, slow operation alerts (>1000ms).

---

## Design Patterns Implementation

The codebase adheres to Gang of Four (GoF) structural, behavioral, and creational design patterns to decouple business rules and promote extensibility:

| Pattern | Architectural Location | Concrete Implementation |
| :--- | :--- | :--- |
| **Strategy Pattern** | `payment-service` | Dynamic calculation of cancellation fees and refund percentages based on elapsed time, driver assignment state, and surge conditions. |
| **Observer Pattern** | All Services | Captures business domain events (e.g., `DriverRatedEvent`, `RidePlacedEvent`) and asynchronously logs them into MongoDB audit collections. |
| **Chain of Responsibility** | `api-gateway` & Services | Ordered security filter pipeline: reactive JWT parsing, claim decoding, header mutation, and correlation ID generation. |
| **Builder Pattern** | All Services | Type-safe construction of composite analytics DTOs (`DriverDashboardDTO`, `RideSummaryDTO`) aggregated across multiple data engines. |
| **Singleton Pattern** | All Services | Centralized, thread-safe configuration management (`JwtConfigurationManager`) ensuring atomic cryptographic key caching. |
| **Factory Pattern** | All Services | Polymorphic instantiation of domain event records and MongoDB audit document models. |
| **Adapter Pattern** | All Services | Translates specialized NoSQL records (Cassandra rows, Neo4j graph nodes, Elasticsearch search hits) into unified domain DTOs. |

---

## Local Development & Quickstart

### Prerequisites

- **Java 25** (OpenJDK) & **Maven 3.9+**
- **Docker Engine 24+** & **Docker Compose v2+**
- **Minikube & kubectl** (for Kubernetes deployment)

### 1. Build Multi-Module Artifacts

```bash
# Build contracts and all microservices
./mvnw clean package -DskipTests
```

### 2. Launch Platform via Docker Compose

```bash
# Boot 5 PostgreSQL databases, 5 NoSQL engines, RabbitMQ, 5 services, and API Gateway
docker compose up -d --build
```

Verify container readiness:
```bash
docker compose ps
```

### 3. Port Allocation & Administrative Interfaces

| Component | Host Port | Internal Port | Credentials / Notes |
| :--- | :---: | :---: | :--- |
| **API Gateway** | `8080` | `8080` | Main ingress endpoint (`http://localhost:8080`) |
| **User Service** | `8081` | `8080` | Internal microservice |
| **Driver Service** | `8082` | `8080` | Internal microservice |
| **Ride Service** | `8083` | `8080` | Internal microservice |
| **Location Service** | `8084` | `8080` | Internal microservice |
| **Payment Service** | `8085` | `8080` | Internal microservice |
| **RabbitMQ Management** | `15672` | `15672` | UI: `guest` / `guest` (`amqp://localhost:5672`) |
| **Neo4j Browser** | `7474` | `7474` | UI: `neo4j` / `neo4jpass` (`bolt://localhost:7687`) |
| **Elasticsearch** | `9200` | `9200` | REST API (`http://localhost:9200`) |
| **MongoDB** | `27017` | `27017` | `root` / `rootpass` (`ubermongo`) |
| **Redis** | `6379` | `6379` | Password: `redispass` |
| **Apache Cassandra** | `9042` | `9042` | CQL native port |

### 4. End-to-End Verification (Smoke Test)

```bash
# 1. Register a Rider
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Smith","email":"alice@example.com","password":"Password123!","role":"RIDER"}'

# 2. Authenticate and retrieve JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"Password123!"}' | jq -r .token)

# 3. Book a Ride via API Gateway
curl -s -X POST http://localhost:8080/api/rides \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"pickupLocation":"30.0444,31.2357","dropoffLocation":"30.0131,31.2089"}'
```

### 5. Deploy to Kubernetes (Minikube)

```bash
# Start Minikube with required memory allocation
minikube start --cpus=4 --memory=8192

# Execute automated deployment script
chmod +x k8s/deploy-all.sh
./k8s/deploy-all.sh --build

# Access API Gateway via NodePort
GATEWAY_URL="http://$(minikube ip):30080"
curl "$GATEWAY_URL/actuator/health"
```

---

## Architectural Evolution & Roadmap

The platform was built through an iterative, milestone-driven engineering process transitioning from a monolithic relational design to a production-grade distributed architecture:

```mermaid
flowchart LR
    subgraph M1 ["Phase 1: Foundation"]
        direction TB
        M1_A["Single Shared PostgreSQL"]
        M1_B["Cross-Service SQL Joins"]
        M1_C["45 Core Business Endpoints"]
        M1_D["Monolithic Transactions"]
    end

    subgraph M2 ["Phase 2: Polyglot & Design Patterns"]
        direction TB
        M2_A["6 Polyglot Storage Engines"]
        M2_B["7 GoF Design Patterns"]
        M2_C["Neo4j Recommendation Engine"]
        M2_D["Cassandra GPS Telemetry Pipeline"]
    end

    subgraph M3 ["Phase 3: True Microservices & K8s"]
        direction TB
        M3_A["Database-per-Service Isolation"]
        M3_B["Declarative OpenFeign RPC"]
        M3_C["RabbitMQ Choreography Saga"]
        M3_D["Spring Cloud Gateway WebFlux"]
        M3_E["Kubernetes StatefulSets & Probes"]
        M3_F["Loki4j + Prometheus Telemetry"]
    end

    subgraph M4 ["Phase 4: Future Scale & Hardening"]
        direction TB
        M4_A["Apache Kafka Streaming Tier"]
        M4_B["Redis GEO Geohash Sharding"]
        M4_C["OpenTelemetry & Grafana Tempo"]
        M4_D["Istio Service Mesh with mTLS"]
    end

    M1 ==> M2 ==> M3 -. Planned .-> M4
```

### Future Engineering Enhancements

- [ ] **Apache Kafka Migration**: Introduce Kafka log-compacted topics for high-throughput driver location streaming, supplementing RabbitMQ for transactional sagas.
- [ ] **Geohash Sharding via Redis GEO**: Implement `GEOADD` and `GEORADIUS` spatial indexing in Redis for sub-millisecond proximity matching.
- [ ] **Distributed Tracing with Tempo**: Upgrade Loki/Prometheus into a complete Grafana LGTM stack by adding Grafana Tempo and OpenTelemetry instrumentation.
- [ ] **Service Mesh (Istio)**: Enforce mTLS encryption between service pods and introduce Canary deployment traffic splitting.

---

## Engineering Team & Attribution

Developed by **RandomUnion (Team 10)** for the **Architecture of Massively Scalable Applications** course at the **German University in Cairo (GUC)** under the academic supervision of **Assoc. Prof. Mervat Abu El-Kheir**.

| Engineer | Student ID | Domain Responsibility | GitHub Profile |
| :--- | :---: | :--- | :--- |
| **Aly Basyouni** | `55-2712` | Driver Service | [@Basyo2003](https://github.com/Basyo2003) |
| **Abdelrahman Khairat** | `55-0254` | Driver Service | [@Abdelrahmankhairat](https://github.com/Abdelrahmankhairat) |
| **Ziad Mahmoud** | `55-1378` | Driver Service | [@ZiadTMahmoud](https://github.com/ZiadTMahmoud) |
| **Youssef Khaled Elshall** | `55-0814` | Location Service | [@Usifkhalid](https://github.com/Usifkhalid) |
| **Ahmed Hany** | `55-8293` | Location Service | [@Shahh03](https://github.com/Shahh03) |
| **Amr Hesham** | `55-0839` | Location Service | [@Amrhesham1](https://github.com/Amrhesham1) |
| **Nour Shalaby** | `55-15679` | User Service | [@nour-shalaby](https://github.com/nour-shalaby) |
| **Ibrahim Ashmawy** | `55-10169` | User Service | [@ibrahim-ashmawy](https://github.com/ibrahim-ashmawy) |
| **Karim Waleed** | `55-15981` | User Service | [@Kareemwaleed1](https://github.com/Kareemwaleed1) |
| **Youssef Abdulazim** | `55-1876` | Ride Service | [@YoussefAmattar](https://github.com/YoussefAmattar) |
| **Zeiad Elenani** | `55-1117` | Ride Service | [@Zeiadenani](https://github.com/Zeiadenani) |
| **Mohamed Elayadi** | `43-2918` | Ride Service | [@ayadi99](https://github.com/ayadi99) |
| **Youssef Elbatrawy** | `55-2005` | Payment Service | [@youssefelbatrawy](https://github.com/youssefelbatrawy) |
| **Kerollos Ashraf** | `55-25131` | Payment Service | [@Kerollos289](https://github.com/Kerollos289) |

---

## License

This project is licensed under the academic evaluation guidelines of the German University in Cairo. All source code is developed for educational and portfolio demonstration purposes.
