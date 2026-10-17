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

A distributed, polyglot ride-hailing backend built with **Spring Boot 4** and **Java 25**. It implements database-per-service isolation, an event-driven choreography Saga over RabbitMQ with automated compensation, high-throughput GPS telemetry ingestion, and Kubernetes orchestration.

---

## System Architecture

Traffic enters through a reactive Spring Cloud Gateway, which validates JWT credentials and propagates correlation IDs. Domain microservices communicate synchronously via OpenFeign for reads and asynchronously via RabbitMQ for writes and saga events.

```mermaid
flowchart TD
    Clients["Client Applications (Rider • Driver • Admin)"]
    Gateway["Spring Cloud API Gateway :8080<br/>(JWT Auth • Dynamic Routing • Correlation ID Injection)"]

    subgraph Services ["Domain Microservices"]
        User["User Service :8081"]
        Driver["Driver Service :8082"]
        Ride["Ride Service :8083"]
        Loc["Location Service :8084"]
        Pay["Payment Service :8085"]
    end

    Broker["RabbitMQ Event Bus (Topic Exchanges & DLQs)"]

    subgraph Storage ["Polyglot Storage Layer"]
        PG[("PostgreSQL 17<br/>5 Isolated DBs")]
        Redis[("Redis<br/>Cache")]
        Mongo[("MongoDB<br/>Audit Log")]
        ES[("Elasticsearch<br/>Driver Search")]
        Neo4j[("Neo4j<br/>Trip Graph")]
        Cassandra[("Cassandra<br/>GPS Pings")]
    end

    Clients --> Gateway
    Gateway --> Services
    Services <--> Broker
    Services --> Storage
```

---

## Services Overview

| Service | Port | Primary Database | Key Responsibilities |
| :--- | :---: | :--- | :--- |
| **`api-gateway`** | `8080` | None (Stateless) | Ingress routing, reactive JWT validation, correlation ID injection. |
| **`user-service`** | `8081` | `uberdb-users` (PostgreSQL) | Authentication (JWT), user registration, role RBAC (`RIDER`, `DRIVER`, `ADMIN`). |
| **`driver-service`** | `8082` | `uberdb-drivers` (PostgreSQL) | Driver profiles, status tracking (`AVAILABLE`, `BUSY`, `OFFLINE`), ratings, fuzzy search (Elasticsearch). |
| **`ride-service`** | `8083` | `uberdb-rides` (PostgreSQL) | Booking lifecycle, surge pricing, driver recommendation graph (Neo4j). |
| **`location-service`** | `8084` | `uberdb-locations` (PostgreSQL) | Real-time GPS ping ingestion (Cassandra), nearby driver discovery. |
| **`payment-service`** | `8085` | `uberdb-payments` (PostgreSQL) | Payment settlement (Card, Wallet, Cash), coupons, dynamic refund calculation. |
| **`contracts`** | N/A | None (Library) | Shared DTOs, RabbitMQ event payloads, and OpenFeign client interfaces. |

---

## Distributed Saga (Ride Completion & Settlement)

Distributed transactions between ride completion and payment settlement are managed via a **Choreography Saga**. On payment failure, an automated 5-hop rollback cascade restores all services to a consistent state.

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Driver
    actor Rider as Rider
    participant RideSvc as Ride Service
    participant Rabbit as RabbitMQ
    participant PaySvc as Payment Service
    participant Others as User / Driver / Location

    Driver->>RideSvc: PUT /api/rides/{id}/complete
    RideSvc->>RideSvc: Set status COMPLETED
    RideSvc->>Rabbit: Publish ride.completed
    
    par Async Fan-Out
        Rabbit->>Others: Set driver AVAILABLE, update rider stats & location
        Rabbit->>PaySvc: Create PENDING payment
    end

    PaySvc->>Rabbit: Publish payment.initiated
    Rabbit->>RideSvc: Set status PAYMENT_PENDING

    Rider->>PaySvc: POST /api/payments/ride/{id}

    alt Payment Succeeded
        PaySvc->>Rabbit: Publish payment.completed
        Rabbit->>RideSvc: Set status PAID (Saga Finished)
    else Payment Failed (Compensating Cascade)
        PaySvc->>Rabbit: Publish payment.failed
        Rabbit->>RideSvc: Set status PAYMENT_FAILED
        RideSvc->>Rabbit: Publish ride.cancelled
        Rabbit->>Others: Rollback rider stats, reset driver earnings
        Rabbit->>PaySvc: Refund payment -> Status REFUNDED
    end
```

---

## Polyglot Persistence

Each storage engine handles workloads tailored to its access pattern:

| Engine | Service | Primary Role | Why Selected |
| :--- | :--- | :--- | :--- |
| **PostgreSQL 17** | All Services | Core transactional data (5 isolated DBs) | ACID guarantees, row-level locking, strict domain encapsulation. |
| **Cassandra** | `location-service` | High-frequency GPS ping streams | High-write throughput, LSM-tree appends, TTL expiration. |
| **Elasticsearch 9** | `driver-service` | Driver catalog search | Fuzzy search, n-gram matching, multi-criteria filtering. |
| **Neo4j** | `ride-service` | Passenger-driver trip graph | Native Cypher traversals for collaborative driver recommendations. |
| **Redis 7** | All Services | In-memory read cache | Sub-millisecond response times, LRU eviction for frequent queries. |
| **MongoDB** | All Services | Audit trail & domain events | Schemaless event storage for immutable audit logging. |

---

## Architecture Evolution

```mermaid
flowchart LR
    subgraph M1 ["Milestone 1: Foundation"]
        M1_1["Shared PostgreSQL DB"]
        M1_2["45 Business Endpoints"]
        M1_3["Cross-Service SQL Joins"]
    end

    subgraph M2 ["Milestone 2: Polyglot & Patterns"]
        M2_1["6 Polyglot Storage Engines"]
        M2_2["7 GoF Design Patterns"]
        M2_3["Neo4j Graph & Cassandra Telemetry"]
    end

    subgraph M3 ["Milestone 3: Microservices & K8s"]
        M3_1["Database-per-Service Isolation"]
        M3_2["RabbitMQ Choreography Saga"]
        M3_3["Spring Cloud Gateway & Minikube"]
    end

    M1 --> M2 --> M3
```

---

## Quickstart

### Prerequisites
- Docker & Docker Compose
- Java 25 & Maven 3.9+ (optional for local builds)
- Minikube & kubectl (optional for Kubernetes)

### 1. Run with Docker Compose
```bash
# Build multi-module JARs
./mvnw clean package -DskipTests

# Start all databases and microservices
docker compose up -d --build
```

### 2. Service Endpoints & Dashboards
- **API Gateway**: `http://localhost:8080` (main entry point)
- **RabbitMQ UI**: `http://localhost:15672` (`guest` / `guest`)
- **Neo4j Browser**: `http://localhost:7474` (`neo4j` / `neo4jpass`)
- **Elasticsearch**: `http://localhost:9200`

### 3. Deploy to Kubernetes
```bash
minikube start --cpus=4 --memory=8192
chmod +x k8s/deploy-all.sh
./k8s/deploy-all.sh --build
```
Access the gateway via NodePort: `http://$(minikube ip):30080`

---

## Team & Attribution

Developed by **RandomUnion (Team 10)** for the **Architecture of Massively Scalable Applications** course at the **German University in Cairo (GUC)** under the supervision of **Assoc. Prof. Mervat Abu El-Kheir**.

| Member | Student ID | Domain | GitHub |
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
