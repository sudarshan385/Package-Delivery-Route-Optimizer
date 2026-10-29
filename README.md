# Package Delivery Route Optimizer

A production-grade, full-stack enterprise route optimization and package delivery lifecycle management platform built with **Java 21**, **Spring Boot 3.5.3**, **React 18 / Vite 5**, and **PostgreSQL 18**.

---

## 1. Project Purpose

The Package Delivery Route Optimizer is designed to solve last-mile delivery logistical challenges by pairing graph-based shortest path route optimization with end-to-end package delivery lifecycle management. The system guarantees atomic state synchronization across packages, deliveries, drivers, and vehicles, enforces strict role-based access control, provides real-time operational analytics, and generates authoritative audit reports.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    React 18 Frontend                         │
│   (Vite 5, Axios Interceptors, Recharts, Lucide React)       │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTP / JSON (Port 5173 /api)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                  Vite Reverse Proxy                          │
│   (Target: http://localhost:8080, changeOrigin: true)        │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                 Spring Boot 3.5.3 Backend                     │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Security Filter Chain (Stateless JWT, BCrypt, Roles)   │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ REST Controllers & DTO Validation Layer                │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Service Layer & Lifecycle State Machines               │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Dijkstra Route Optimizer Engine                        │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Spring Data JPA / Hibernate (ddl-auto=validate)        │  │
│  └───────────────────────────┬────────────────────────────┘  │
└──────────────────────────────┼───────────────────────────────┘
                               │ JDBC (HikariPool)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                 PostgreSQL 18 Database                       │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Flyway V1 Schema Migrations (12 Relational Tables)     │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Foreign Key Constraints (ON DELETE RESTRICT)           │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack

| Layer | Component | Specification |
| :--- | :--- | :--- |
| **Language** | Java | **Java 21** (`--release 21` enforced) |
| **Backend Framework** | Spring Boot | **3.5.3** (Web, Data JPA, Security, Validation) |
| **Security** | Spring Security | JWT (`jjwt 0.12.6`), BCrypt Password Hashing, Stateless Sessions |
| **Persistence** | Hibernate ORM | `spring.jpa.hibernate.ddl-auto=validate` (Zero runtime schema changes) |
| **Migrations** | Flyway | Flyway Core + Flyway PostgreSQL (`classpath:db/migration`) |
| **Database** | PostgreSQL | PostgreSQL 18.x (Relational Schema with 12 Normalized Tables) |
| **Frontend Framework** | React | React 18.3.1 (React Router DOM v6, Axios, Recharts, Lucide React) |
| **Build Tooling** | Vite | Vite 5.2.12 (ES Modules, Hot Module Replacement, Reverse Proxy) |
| **Testing** | JUnit 5 & Node Test | Spring Boot Test / MockMvc (127 Integration Tests) + Node.js Native Runner (6 Tests) |

---

## 4. Prerequisites

- **Java Development Kit (JDK)**: JDK 21 or higher (JDK 21 LTS recommended)
- **Apache Maven**: Version 3.8+ (or Maven 3.9+)
- **Node.js**: Version 18.x or 20.x or 24.x
- **PostgreSQL**: Version 14 or higher (18.x verified)

---

## 5. PostgreSQL Database Setup

1. The project uses PostgreSQL 18 with dedicated database identity verification:
   - **Development Database**: `packagedeliveryrouteoptimizer` (user: `app_user`)
   - **Test Database**: `route_optimizer_test_db` (user: `postgres`)
2. Verify credentials match your local configuration (defaults: `host=localhost`, `port=5432`, `username=app_user`).

---

## 6. Environment Variables & Configuration

Backend development configuration is defined in [`backend/src/main/resources/application-dev.properties`](file:///s:/MAJOR%20PROJECT/backend/src/main/resources/application-dev.properties) and [`application.properties`](file:///s:/MAJOR%20PROJECT/backend/src/main/resources/application.properties):

| Property / Env Var | Default Dev Value | Description |
| :--- | :--- | :--- |
| `server.port` | `8080` | Backend HTTP port |
| `DEV_DB_URL` | `jdbc:postgresql://localhost:5432/packagedeliveryrouteoptimizer` | Development PostgreSQL connection URL |
| `DEV_DB_USERNAME` | `app_user` | Development database user |
| `DEV_DB_PASSWORD` | `app_password` | Development database password |
| `JWT_SECRET` | `dev-suite-dedicated-hmac-sha256-signing-key-min-32-bytes` | 256-bit (>=32 bytes) HMAC-SHA256 signing secret |
| `JWT_EXPIRATION_MS` | `900000` | Token lifetime: 15 minutes (900,000 ms) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174,http://localhost:3000` | Allowed CORS origins |
| `spring.flyway.enabled` | `true` | Executes Flyway migrations V1–V9 on startup |
| `spring.jpa.hibernate.ddl-auto`| `validate` | Validates JPA entities against Flyway schema |

---

## 7. Starting the Application

### Backend Startup
The backend must run with **Java 21** and the **`dev`** profile. Use the official startup script:
```powershell
.\start-backend.ps1
```
Or directly from `backend/`:
```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```
Tomcat will initialize on port `8080`. Database identity verification (`DatabaseIdentityVerifier`) will ensure the connected database is `packagedeliveryrouteoptimizer`, Flyway will validate migrations V1–V9, and Hibernate will validate all JPA entities without altering the database schema.

To stop the backend gracefully:
```powershell
.\stop-backend.ps1
```

### Frontend Startup
```bash
cd frontend
npm install
npm run dev
```
Vite will start on `http://localhost:5173`.

---

## 8. Initial Admin Setup & Authentication

1. **Bootstrap Condition**: When the `users` table contains zero records, `GET /api/auth/setup/status` returns:
   ```json
   { "setupRequired": true, "message": "Initial system setup is available..." }
   ```
2. **Initial Administrator Registration**: Open `http://localhost:5173/setup` or execute:
   ```http
   POST /api/auth/setup
   Content-Type: application/json

   {
     "username": "admin",
     "email": "admin@optimizer.com",
     "password": "AdminPassword123",
     "fullName": "System Administrator"
   }
   ```
3. **Immutability Guarantee**: Once the first administrator is created, setup transitions to `setupRequired = false`. Subsequent attempts to access `/api/auth/setup` are safely rejected with `409 Conflict`.
4. **Authentication**: All protected endpoints require a signed Bearer token:
   ```http
   Authorization: Bearer <accessToken>
   ```

---

## 9. API Overview & Endpoint Matrix

| Method | Endpoint | Authorized Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/auth/setup/status` | Public | Check if initial bootstrap is required |
| `POST` | `/api/auth/setup` | Public | Create initial ADMIN account |
| `POST` | `/api/auth/login` | Public | Authenticate and obtain JWT access token |
| `GET` | `/api/dashboard` | ADMIN, DRIVER, USER | Aggregated real-time dashboard metrics |
| `GET` | `/api/locations` | ADMIN, DRIVER | List locations (paginated) |
| `POST` | `/api/locations` | ADMIN | Create delivery location |
| `PUT` | `/api/locations/{id}` | ADMIN | Update location details |
| `DELETE`| `/api/locations/{id}` | ADMIN | Soft-deactivate location |
| `GET` | `/api/edges` | ADMIN, DRIVER | List road edges |
| `POST` | `/api/edges` | ADMIN | Create road edge between locations |
| `GET` | `/api/drivers` | ADMIN, DRIVER | List drivers |
| `POST` | `/api/drivers` | ADMIN | Create driver profile |
| `GET` | `/api/vehicles` | ADMIN, DRIVER | List vehicles |
| `POST` | `/api/vehicles` | ADMIN | Create vehicle profile |
| `GET` | `/api/packages` | ADMIN, DRIVER, USER | List packages |
| `POST` | `/api/packages` | ADMIN, USER | Create package |
| `GET` | `/api/deliveries` | ADMIN, DRIVER, USER | List deliveries |
| `POST` | `/api/deliveries` | ADMIN | Create delivery for package |
| `POST` | `/api/deliveries/{id}/assign` | ADMIN | Assign driver & vehicle to delivery |
| `POST/PATCH`| `/api/deliveries/{id}/status`| ADMIN, DRIVER | Advance delivery lifecycle state |
| `POST` | `/api/optimization` | ADMIN | Calculate Dijkstra shortest path |
| `GET` | `/api/routes/{id}` | ADMIN, DRIVER | Retrieve persisted route & stops |
| `GET` | `/api/analytics` | ADMIN | Query business KPIs and utilization stats |
| `GET` | `/api/reports/deliveries/csv`| ADMIN | Download deliveries audit CSV |
| `GET` | `/api/reports/routes/csv` | ADMIN | Download routes audit CSV |
| `GET` | `/api/users` | ADMIN | List registered users |
| `POST` | `/api/users` | ADMIN | Create user (ADMIN, DRIVER, USER) |

---

## 10. Dijkstra Route Optimization Algorithm

The routing engine implements Dijkstra's single-source shortest path algorithm using a Min-Priority Queue on a directed weighted road graph:

1. **Graph Construction**: The directed road network is constructed from active `location_edges` retrieved from PostgreSQL (`findByActiveTrue()`). Only edges connecting active source and destination locations are added to the adjacency list.
2. **Shortest Path Determination**: Starting at the designated source location, distances to all reachable nodes are relaxed iteratively:
   $$\text{dist}(v) = \min(\text{dist}(v), \text{dist}(u) + w(u, v))$$
   where $w(u, v)$ represents the physical road distance (`distance` column in kilometers).
3. **Path Reconstruction**: Once the destination node is reached, the optimal sequence of locations and edges is backtracked using the predecessor map.
4. **Authoritative Travel Time Preservation**:
   - Each `LocationEdge` stores an authoritative travel time (`estimated_time`).
   - The total estimated route time is calculated as the exact sum of the stored travel times along the chosen path:
     $$\text{totalEstimatedTime} = \sum_{e \in \text{Path}} \text{edge.estimatedTime}$$
   - When travel time is omitted during edge creation, the system computes a fallback estimate using a baseline speed of 40 km/h:
     $$\text{estimatedTime} = \frac{\text{distance}}{40.0} \times 60.0 \text{ minutes}$$
   - No hidden artificial transformations or speed alterations are applied during optimization.
5. **Route Persistence & Versioning**:
   - When optimization is performed for an active delivery, the route is persisted in the `routes` table with ordered `route_stops` (SOURCE, WAYPOINT, DESTINATION) and `route_edges`.
   - Subsequent re-optimizations increment the `version_number` and mark previously generated routes as `is_current = false`.

---

## 11. Delivery Lifecycle State Machine

Deliveries follow a strictly validated state transition flow:

```
[CREATED] ──(Assign Driver & Vehicle)──► [ASSIGNED]
                                              │
                                     (Start Delivery)
                                              ▼
                                       [IN_PROGRESS]
                                              │
                                    (Complete Delivery)
                                              ▼
                                         [COMPLETED]
```

- **Resource Locking & Synchronization**:
  - `ASSIGNED`: Delivery is assigned to driver and vehicle. Package status transitions to `IN_DELIVERY`. Driver status becomes `ON_DELIVERY`. Vehicle status becomes `IN_USE`.
  - `COMPLETED`: Delivery reaches final completion. Package status transitions to `DELIVERED`. Driver and vehicle are automatically released back to `AVAILABLE`.
  - `CANCELLED` / `FAILED`: Cancelling an assigned or in-progress delivery automatically releases the driver and vehicle back to `AVAILABLE`.

---

## 12. Testing Commands

### Backend Regression Tests
Run the complete backend integration and unit test suite (127 tests):
```bash
cd backend
mvn test
```

### Frontend Unit & State Tests
Run the frontend automated unit test suite (6 tests):
```bash
cd frontend
npm test
```

### Production Bundling
Verify production compilation:
```bash
cd frontend
npm run build
```

---

## 13. Project Limitations

The current production release focuses strictly on deterministic, graph-based route optimization and delivery lifecycle integrity. The following features are intentionally **NOT** included in this release:

1. **No Live GPS Streaming**: Driver and vehicle locations are updated via discrete location milestones, not continuous GPS telemetry.
2. **No Real-Time Traffic Ingestion**: Road travel times are based on authoritative historical and configured edge metrics rather than dynamic live traffic feeds (e.g. Google Maps API).
3. **No External Geocoding**: Location coordinates (latitude/longitude) must be provided directly during location creation.
4. **No Multi-Vehicle Capacitated VRP**: The system solves single-delivery point-to-point and intermediate stop shortest path routing; it does not compute multi-vehicle fleet combinatorial TSP/VRP routes.
5. **No Payment Gateway / Billing**: Payment processing is outside the major project operational scope.
6. **No SMS / Push Notifications**: Operational updates are handled in-app via REST and Web dashboards.

---

## 14. Future Research Extension: DDQN-PPDRP

| Distinction | Description |
| :--- | :--- |
| **CURRENT IMPLEMENTATION** | **Dijkstra-based Route Optimization**: Deterministic shortest-path graph algorithm utilizing exact edge weights, persistent route versioning, and real-time state synchronization. Fully implemented and verified. |
| **FUTURE RESEARCH EXTENSION** | **DDQN-PPDRP (Double Deep Q-Network for Pickup and Delivery Route Planning)**: Reinforcement learning extension designed to train autonomous dispatch agents to dynamically balance stochastic delivery requests, vehicle battery/fuel consumption, and dynamic traffic fluctuations across a continuous state-action space. |

---

## 15. License

Academic Final-Year Major Project — All rights reserved.
