# Project Memory — Package Delivery Route Optimizer

Persistent project state, architectural decisions, verified deliverables, known issues, and roadmap items for the Package Delivery Route Optimizer platform.

> **Security Guard**: Never store passwords, tokens, BCrypt hashes, connection secrets, or private keys in this document or any other documentation file.

---

## 1. Project Context & Environment

| Property | Value | Notes |
| :--- | :--- | :--- |
| **Project Root** | `S:\MAJOR PROJECT` | Monorepo layout (`backend/`, `frontend/`, `research-data/`, `scripts/`, `docs/`) |
| **Backend Runtime** | Java 21 LTS | Maven build, Spring Boot 3.5.3, port `8080` |
| **Frontend Runtime** | Node.js 18+/20+, Vite 5.2.12 | React 18.3.1, port `5173` (Vite reverse proxy `/api` -> `http://localhost:8080`) |
| **Database Engine** | PostgreSQL 18.x | 64-bit on `localhost:5432` |
| **Development Database** | `packagedeliveryrouteoptimizer` | Default user: `app_user` (defined in `application-dev.properties`) |
| **Test Database** | `route_optimizer_test_db` | Dedicated isolated database for JUnit tests (user: `postgres`) |
| **Schema Migration** | Flyway Core 10.x | 7 migration scripts (`V1` to `V7`) located in `classpath:db/migration` |
| **JPA DDL Auto** | `validate` | Zero runtime schema modification; Flyway is authoritative |

---

## 2. Current Project State Summary

The core application is fully operational and functionally verified across both backend integration tests (127 passed tests) and frontend state tests (6 passed tests). The platform provides end-to-end delivery management from customer order placement to administrative resource assignment, deterministic Dijkstra route calculation with real OSRM road geometry, and delivery status transitions to final completion. The DDQN-PPDRP research pipeline is isolated and provides a sandbox dynamic replanning demonstration without affecting production tables.

---

## 3. Verified Completed Work

### 3.1 Authentication, Security & Account Governance
- [x] **Advisory-Locked Bootstrap**: Initial administrator creation (`POST /api/auth/setup`) utilizes PostgreSQL transaction advisory lock `8844221199L`, preventing race conditions during bootstrap.
- [x] **Stateless JWT Security**: HMAC-SHA256 tokens with 15-minute expiration (`app.jwt.expiration-ms=900000`).
- [x] **Three-Role Model**: Strict enforcement of `ADMIN`, `DRIVER`, and `USER` roles.
- [x] **Dispatcher Deprecation**: Role `DISPATCHER` was deprecated, existing accounts migrated to `ADMIN`, and a check constraint added via Flyway migration `V7__remove_dispatcher_role.sql`.
- [x] **User Ownership Isolation**: Customer users (`USER`) can only view and manage packages and deliveries linked to their account (`customer_id == user.id`).
- [x] **Driver Delivery Isolation**: Drivers can only view and transition deliveries assigned to them (`delivery.driver.user.id == user.id`).
- [x] **Admin Password Rotation**:
  - Out-of-band operational PowerShell script: `backend/scripts/Rotate-AdminPassword.ps1` and `scripts/maintenance/db-update-password.ps1` update administrator passwords using PostgreSQL `pgcrypto` BCrypt hashing without modifying user IDs, roles, or attributes.
  - In-app REST endpoint: `PUT /api/users/profile/password` allows authenticated users to rotate their own password after verifying their current password.
  - Verified by `AdminUserRecoveryTest.java` (no duplicate accounts, bad credential rejection).

### 3.2 Master Data & Infrastructure Management
- [x] **Locations & Edges**: Full CRUD for graph vertices (`locations`) and directed road segments (`location_edges`).
- [x] **Customer Geocoding & Approval Workflow**:
  - Non-master customer addresses are geocoded via OpenStreetMap Nominatim (`GeocodingService`) into pending `Location` records (`active = false`).
  - Admins approve and link pending locations to active hub/depot vertices via `POST /api/locations/{id}/approve` (`AdminApprovalWorkflowIntegrationTest.java`).
- [x] **Fleet Assets**: CRUD management for drivers (with license numbers) and vehicles (with payload capacities).
- [x] **Database Safety Guards**:
  - `DatabaseIdentityVerifier.java`: Verifies database name against the active Spring profile (`packagedeliveryrouteoptimizer` for `dev`, `route_optimizer_test_db` for `test`) before Flyway migrations and post-startup.
  - `TestDatabaseGuard.java`: Aborts any destructive test operation if the connected database is not `route_optimizer_test_db`.
  - Prohibits fake business records, test data injection, or schema resets against the development database.

### 3.3 Routing Engine & OSRM Road Geometry
- [x] **Java Dijkstra Routing Engine**: Deterministic shortest path calculation using a Min-PriorityQueue over active graph edges. Supports `SHORTEST_DISTANCE` (km) and `FASTEST_ROUTE` (travel time minutes) criteria.
- [x] **Authoritative Travel Time**: Sums exact `estimated_time` values stored on traversed edges; applies `(distance / 40.0) * 60.0` minutes fallback when omitted.
- [x] **OSRM Road Geometry Integration**:
  - Integrates with OSRM HTTP API (`RoutingService`) to fetch distance (km), duration (min), and GeoJSON `LineString` geometries.
  - Persisted in `location_edges.geometry_geojson` and snapshotted into `route_edges.geometry_geojson` (`V6__add_geometry_geojson_to_edges.sql`).
  - React Leaflet map (`Map.jsx`) renders real road paths in green, fallback straight connections in dashed amber, and displays a geometry status badge.
- [x] **Route Versioning & Snapshots**: Generates ordered `route_stops` and `route_edges`, increments `version_number`, marks old routes as `is_current = false`, and syncs estimated metrics to the delivery record.

### 3.4 Delivery Lifecycle State Machine
- [x] **State Flow**: `CREATED` $\rightarrow$ `ASSIGNED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`, with branching to `CANCELLED` (Admin only) or `FAILED` (Driver/Admin).
- [x] **Pessimistic Concurrency Locking**: Uses `findByIdWithLock` (`PESSIMISTIC_WRITE`) during assignment and status transitions.
- [x] **Resource Synchronization**:
  - `ASSIGNED`: Package becomes `IN_DELIVERY`, Driver becomes `ON_DELIVERY`, Vehicle becomes `IN_USE`.
  - `COMPLETED` / `FAILED` / `CANCELLED`: Driver and Vehicle are automatically released back to `AVAILABLE`.
- [x] **Audit History**: Every transition is immutably recorded in `delivery_status_history`.

### 3.5 Automated Backups & Maintenance
- [x] **Automated Daily Backup Task**: Windows Scheduled Task registered via `backend/scripts/Register-RouteOptimizerBackupTask.ps1` calling `backup-database.ps1`.
  - Uses `pg_dump -F c` (custom binary format).
  - Authenticates securely via `PGPASSFILE` (`C:\backups\pgpass.conf`).
  - Verifies archive integrity using `pg_restore --list`.
  - Automatically purges backups older than 14 days.

### 3.6 Research Extension: DDQN-PPDRP
- [x] **Isolated Research Module**: Resides in `backend/src/main/java/com/routeoptimizer/research/`. Evaluates Amazon Last Mile dataset (`research-data/`) for Complete Match Rate (CMR) and Edit Distance (ED).
- [x] **Dynamic Replanning Demo**: Frontend page (`AIOptimization.jsx`) and backend endpoint (`POST /api/experiments/ddqn/replan`) simulate dynamic order insertions at $t > 0$ using the research policy without modifying production deliveries.

---

## 4. Key Architectural Decisions

1. **Single Source of Truth**: Java Spring Boot is the absolute authority for routing algorithms, lifecycle state machines, authorization, and data validation. Frontend logic handles rendering and user interaction only.
2. **Deterministic Production Router vs AI Research**: Production routing strictly uses the deterministic Java Dijkstra algorithm. The DDQN reinforcement learning pipeline is an isolated research experiment.
3. **Database Guard Architecture**: Destructive test operations and administrative scripts are gated by programmatic guards (`TestDatabaseGuard`, `DatabaseIdentityVerifier`) to prevent accidental writes or resets to the development database.
4. **Zero Runtime DDL**: Hibernate DDL generation is disabled (`ddl-auto=validate`). All database evolutions are managed strictly through versioned Flyway migrations.
5. **Pessimistic Resource Locking**: Resource allocation (Driver, Vehicle, Delivery) uses database-level row locks during assignment to prevent double-booking under concurrent access.

---

## 5. Known Issues & Quirks

1. **Public OSRM & Nominatim Dependencies**:
   - `RoutingService` connects to public `http://router.project-osrm.org`.
   - `GeocodingService` connects to public `https://nominatim.openstreetmap.org`.
   - Both are subject to external rate limits, network latency, and third-party downtime. A polite rate-limiting sleep is implemented in both services. Production deployments should self-host a local OSRM instance.
2. **Historical Documentation Drift**:
   - `README.md` previously claimed "No external geocoding" and "No real-time routing / OSRM", whereas `GeocodingService` and `RoutingService` are fully implemented in active source code.
   - `README.md` listed default database URL as `route_optimizer_db` with credentials `postgres/root`, whereas `application-dev.properties` uses `packagedeliveryrouteoptimizer` with `app_user/app_password`.
   - `openapi.yaml` omitted several active endpoints (`/api/auth/register`, `/api/locations/geocode`, `/api/locations/{id}/approve`, `/api/experiments/ddqn/...`).
3. **Standalone vs Delivery Route Persistence**: Calling `POST /api/optimization` without a `deliveryId` calculates Dijkstra routes in-memory for previewing, but only persists `routes`, `route_stops`, and `route_edges` when a valid `deliveryId` is provided.

---

## 6. Pending & Unverified Work

### 6.1 Genuinely Pending Work
- [ ] **Self-Hosted OSRM Deployment**: Provision a local Docker container running OSRM for local road routing without relying on the public `router.project-osrm.org` endpoint.
- [ ] **Capacitated Vehicle Routing Problem (CVRP)**: Multi-vehicle, multi-stop combinatorial fleet optimization (currently, Dijkstra optimizes single-delivery point-to-point and intermediate waypoints).
- [ ] **OpenAPI Specification Synchronization**: Update `backend/src/main/resources/openapi.yaml` to include all newly implemented endpoints (`/register`, `/geocode`, `/approve`, `/replan`).

### 6.2 Unverified Items
- [ ] **High-Concurrency Telemetry Load**: The system uses discrete milestone status updates rather than continuous GPS streaming; high-frequency GPS ping ingestion has not been benchmarked.
- [ ] **Multi-Hub Graph Scaling**: Dijkstra performance across very large graph topologies (>10,000 vertices) has not been benchmarked against a production database dataset.
