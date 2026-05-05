# Fix Failing Tests — 347/425 → Target 400+/425

## Context
72 tests are failing/erroring (31 FAILs + 47 ERRORs). Failures are spread across
5 services (user, driver, ride, payment, location) plus design-pattern checks. Root
causes fall into three buckets:
1. **Wrong HTTP status codes** – endpoints throw 400 where spec requires 404/403/2xx
2. **Missing validation** – inputs not validated → 500 instead of 400
3. **Logic bugs** – calculation errors, unchecked cache returns empty, comment contains `@Configuration`
4. **Infrastructure** – docker-compose missing healthcheck deps → services start before
   Cassandra/Neo4j are ready → timeout

---

## Group A — docker-compose.yaml (fixes ~30 timeout ERRORs)

### A1 — Restore `cassandra-init` (removed in last commit)
Without it the `uberks` keyspace is never created; location-service Cassandra ops hang.

### A2 — ride-service: add neo4j + mongo + redis health deps
Currently ride-service only waits for `postgres`. Neo4j/Mongo/Redis may not be ready
when ride-service starts, causing Neo4j graph ops (recommendations, record-ride) to hang.

### A3 — payment-service: add mongo + redis health deps

### A4 — driver-service: pass MongoDB + Redis env vars explicitly

---

## Group B — user-service fixes

### B1 — TC17/TC18/TC19: IDOR protection on GET/PUT/DELETE /api/users/{id}
- `getUserById`, `updateUser`, `deleteUser` need same IDOR pattern as `getUserActivityFeed`
- Return 403 if `requesterId != id` AND role not ADMIN; 401 if no JWT attributes

### B2 — TC29/TC33: Reject negative page/size with 400
- Replace `Math.max`/`Math.min` normalization with explicit 400 for negatives

### B3 — TC211: Foreign address → 404 not 400
- `SavedAddressService.java`: change `BAD_REQUEST` → `NOT_FOUND` for foreign address

---

## Group C — ride-service fixes

### C1 — TC260: completionRate must be 0–1 decimal, not 0–100
- `RideService.java` line 319: remove `* 100`

### C2 — TC256/TC344: completeRide fails because driver not BUSY
- Remove `busyDriver == 0` guard (lines 170–175) — not a valid precondition

### C3 — TC255: Missing dropoff → 500 instead of 400
- Add `@NotNull` to `FareEstimateRequestDTO` fields; add `@Valid` in controller

### C4 — TC249/TC268/TC342: search endpoint returns 400 because dates required
- Make `startDate`/`endDate` optional; add optional `userId` param for rider scope

### C5 — TC264/TC265: addStops — allow stops on IN_PROGRESS rides too
- Change status check: only reject COMPLETED/CANCELLED, allow any other status

### C6 — TC49/TC52: Add FK from rides → drivers (shared DB)
- Create `DriverRef` entity in ride-service mapping to `drivers` table
- Add `@ManyToOne @JoinColumn` on `Ride.driverId`

---

## Group D — driver-service fixes

### D1 — TC338: Unknown vehicle type → 400 not 200
- Validate against `ALLOWED_VEHICLE_TYPES` before querying; throw 400 if unknown

### D2 — TC341: Verify-doc role check must come BEFORE document validation
- Move role/ADMIN check to top of `verifyDocument` method

### D3 — TC238/TC239: verifyDocument returns wrong status codes
- Document not belonging to driver → 404 (not 400)

---

## Group E — payment-service fixes

### E1 — TC322: Invalid payment method → 400 not 500
- Wrap `PaymentMethod.valueOf()` in try-catch; throw 400 on `IllegalArgumentException`

### E2 — TC360: Payment search returns empty when no dates passed
- Remove early-return on null dates; allow unbounded search

### E3 — TC365: timesUsed = 0
- Add `@Transactional` to `applyCoupon`; verify cache eviction fires after DB commit

---

## Group F — location-service fixes

### F1 — TC129: Timeline time-range returns 400 (Instant parse failure)
- Change `Instant` params to `LocalDateTime` with `@DateTimeFormat(iso=DATE_TIME)`

---

## Group G — Design Pattern fixes

### G1 — TC382/TC383: strategyName missing from refund audit doc
- In `refundSurgeAdjusted`, pass `strategyName` into observer notification data

### G2 — TC402: TopDriverDTO missing (file not found)
- Create `driver-service/.../dto/TopDriverDTO.java` with Builder pattern

### G3 — TC410: JwtConfigurationManager comment contains `@Configuration`
- Change comment in all 5 `JwtConfigurationManager.java` files to not contain `@Configuration`

### G4 — TC419: PaymentService directly constructs PaymentAuditEvent with `new`
- Replace `new PaymentAuditEvent(...)` with `EventFactory.createEvent()`

---

## Execution Order
1. docker-compose.yaml (A1–A4)
2. ride-service (C1–C6)
3. user-service (B1–B3)
4. driver-service (D1–D3)
5. payment-service (E1–E3)
6. location-service (F1)
7. Design patterns (G1–G4)

## Status
- [x] A1 cassandra-init restored
- [x] A2 ride-service deps (neo4j + mongo + redis health checks)
- [x] A3 payment-service deps (mongo + redis health checks)
- [x] A4 driver-service env vars (MongoDB, Redis, Elasticsearch)
- [x] B1 IDOR protection on getUserById/updateUser/deleteUser
- [x] B2 negative pagination → 400
- [x] B3 foreign address → 404
- [x] C1 completionRate fix (removed *100)
- [x] C2 remove busyDriver check from completeRide
- [x] C3 FareEstimate null check → 400
- [x] C4 optional dates in search + userId param
- [x] C5 addStops status check (only reject COMPLETED/CANCELLED)
- [x] C6 rides→drivers FK (DriverRef entity + @ManyToOne)
- [x] D1 vehicle type validation (throw 400 for unknown type)
- [x] D2 role check BEFORE document validation
- [x] D3 document not belonging → 404
- [x] E1 invalid payment method → 400
- [x] E2 payment search no dates → unbounded search
- [x] E3 applyCoupon @Transactional
- [x] F1 timeline LocalDateTime (Instant → LocalDateTime conversion)
- [x] G1 strategyName in refund audit doc
- [x] G2 TopDriverDTO created with Builder pattern
- [x] G3 comment cleanup (removed @Configuration from JwtConfigurationManager comments)
- [x] G4 EventFactory in saveAuditEvent (no direct new PaymentAuditEvent)
