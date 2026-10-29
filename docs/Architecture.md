# System Architecture — Package Delivery Route Optimizer

Authoritative technical architecture documentation for the Package Delivery Route Optimizer platform. This document reflects the verified implementation across the React frontend, Spring Boot backend, PostgreSQL database, and the isolated DDQN research module.

---

## 1. High-Level System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                      Client Browser (React 18 SPA)                     │
│  - React Router DOM v6 (Role-guarded routes)                           │
│  - Axios with Request/Response Interceptors (Bearer JWT, Error Handler)│
│  - Leaflet Map + OpenStreetMap Tiles (OSRM GeoJSON Polyline Overlay)   │
│  - Recharts Visualizations & Modern CSS Design Token System            │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / JSON (Port 5173 /api)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Vite 5 Reverse Proxy Server                       │
│  - Proxy rule: '/api' -> 'http://localhost:8080'                       │
│  - changeOrigin: true                                                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST (Port 8080)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3.5.3 Backend Server                   │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Security Filter Chain                                            │  │
│  │  - JwtAuthenticationFilter (Stateless Bearer validation)         │  │
│  │  - BCryptPasswordEncoder (Salt rounds: 10)                       │  │
│  │  - Method Security (@PreAuthorize hasRole / hasAnyRole)          │  │
│  │  - ProblemDetails RFC 7807 Exception Handlers (401 / 403)        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ REST Controllers & DTO Validation Layer                          │  │
│  │  - AuthController, UserController, LocationController            │  │
│  │  - LocationEdgeController, DriverController, VehicleController   │  │
│  │  - PackageController, DeliveryController, OptimizationController │  │
│  │  - RouteController, AnalyticsController, ReportController        │  │
│  │  - MatrixAuthorizationCheckController (Section 44 Matrix)        │  │
│  │  - ExperimentController (DDQN Research API)                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Core Service Layer & Business Logic                              │  │
│  │  - DeliveryService (Atomic lifecycle state machine, resource lock)│ │
│  │  - RouteService (Route persistence, stop snapshots, versioning)  │  │
│  │  - LocationEdgeService & LocationService (Approval workflow)     │  │
│  │  - GeocodingService (Nominatim API client with rate limiter)     │  │
│  │  - RoutingService (OSRM road geometry & metrics client)          │  │
│  │  - AuthService & UserService (Setup lock, user management)       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Production Routing Engine: Java Dijkstra Algorithm               │  │
│  │  - Min-PriorityQueue graph traversal                             │  │
│  │  - Supported criteria: SHORTEST_DISTANCE, FASTEST_ROUTE          │  │
│  │  - Authoritative travel time summation                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Isolated DDQN-PPDRP Research Module                              │  │
│  │  - Deep Q-Network policy & Double DQN replanning                 │  │
│  │  - TSM-TW candidate generation & APER prioritized replay         │  │
│  │  - Amazon Last Mile historical dataset adapter                   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Data Access & Persistence Layer (Spring Data JPA / Hibernate)    │  │
│  │  - Pessimistic locking (findByIdWithLock)                        │  │
│  │  - DatabaseIdentityVerifier (Dev/Test DB safety guard)           │  │
│  │  - ddl-auto=validate (Zero runtime schema modification)          │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │ JDBC (HikariCP)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        PostgreSQL 18 Database                          │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Flyway Migration Engine (classpath:db/migration)                 │  │
│  │  - V1__initial_schema.sql (11 core relational tables)            │  │
│  │  - V2__add_package_city_and_custom_locations.sql                 │  │
│  │  - V3__add_ddqn_ppdrp_research_schema.sql (Research tables)      │  │
│  │  - V4__add_customer_id_to_packages.sql                           │  │
│  │  - V5__add_city_to_locations.sql                                 │  │
│  │  - V6__add_geometry_geojson_to_edges.sql (OSRM geometry)         │  │
│  │  - V7__remove_dispatcher_role.sql (3-role CHECK constraint)      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Target Databases                                                 │  │
│  │  - Development: packagedeliveryrouteoptimizer (user: app_user)   │  │
│  │  - Test: route_optimizer_test_db (user: postgres)               │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Responsibilities

### 2.1 Frontend (`frontend/src/`)
- **App Routing (`App.jsx`)**: Manages navigation using `react-router-dom` v6. Routes are guarded based on authenticated user session and role (`ADMIN`, `DRIVER`, `USER`).
- **Authentication State (`context/AuthContext.jsx`)**: Tracks active user, JWT token, setup status, login, logout, and automatic header injection via Axios interceptors.
- **API Client (`api/axios.js`)**: Configures Axios instance with `baseURL: '/api'`, Bearer token interceptor, and 401 response handling.
- **Mapping (`components/ui/Map.jsx`)**: Renders interactive OpenStreetMap via React Leaflet. Visualizes vertices, background graph connections, and active Dijkstra routes using real OSRM GeoJSON polylines (with dashed fallback when geometry is absent).
- **Page Views (`pages/`)**: Implements role-specific views for Dashboard, Deliveries, Route Optimizer, Packages, Locations, Drivers, Vehicles, Users, Analytics, Reports, AI Optimization, and User Profile.

### 2.2 Backend (`backend/src/main/java/com/routeoptimizer/`)
- **Security & Identity Verification (`config/`, `security/`)**:
  - `DatabaseIdentityVerifier`: Intercepts Pre-Flyway and Post-Startup lifecycles to ensure the active Spring profile matches the connected database name (`packagedeliveryrouteoptimizer` for `dev`, `route_optimizer_test_db` for `test`).
  - `SecurityConfig`: Enforces stateless sessions, CSRF protection bypass for REST, CORS whitelist, and delegates endpoint access via Spring Security's `@EnableMethodSecurity`.
  - `JwtTokenProvider`: Issues and validates HMAC-SHA256 signed JWTs with 15-minute validity (`app.jwt.expiration-ms=900000`).
- **Master Data & Operations (`service/`, `controller/`)**:
  - `LocationService` & `LocationEdgeService`: Manage road network nodes and directed edges. Handles the customer location approval workflow (`/api/locations/{id}/approve`).
  - `RoutingService`: Integrates with external Open Source Routing Machine (OSRM) HTTP API to calculate real road distance (km), duration (min), and GeoJSON geometries.
  - `GeocodingService`: Integrates with OpenStreetMap Nominatim API for forward geocoding with built-in rate-limiting and fallback coordinates.
  - `DriverService` & `VehicleService`: Manage fleet resources and availability status.
  - `PackageService`: Manages customer package records, customer data ownership, and automatic pending location creation for new addresses.
  - `DeliveryService`: Executes the atomic delivery lifecycle state machine, enforces pessimistic resource locking, and logs status audit history.
  - `RouteService`: Optimizes paths via Dijkstra, snapshots ordered stops and edges, tracks version numbers, and updates delivery travel estimates.
  - `DashboardService` & `AnalyticsService`: Calculate real-time operational metrics and historical KPIs with role-scoped filtering.
  - `ReportService`: Streams authoritative CSV audit reports for deliveries and routes.
- **Production Optimization (`optimizer/`)**:
  - `DijkstraRouteOptimizationStrategy`: Implements Dijkstra's algorithm using a Min-PriorityQueue over active directed graph edges. Supports `SHORTEST_DISTANCE` and `FASTEST_ROUTE` criteria.
- **Isolated Research Module (`research/`)**:
  - Encapsulates Double Deep Q-Network for Pickup and Delivery Route Planning (DDQN-PPDRP), Amazon Last Mile dataset evaluation, Action-Prioritized Experience Replay (APER), and dynamic replanning simulations via `/api/experiments/ddqn/replan`.

---

## 3. Communication Protocols & REST API Architecture

### 3.1 Protocol Specifications
- **Format**: All payloads use HTTP/1.1 with JSON (`Content-Type: application/json;charset=UTF-8`).
- **Reports**: Audit exports stream `text/csv; charset=UTF-8` with `Content-Disposition: attachment`.
- **Date/Time Standard**: ISO-8601 with timezone offsets (`TIMESTAMP WITH TIME ZONE` in PostgreSQL, `OffsetDateTime` in Java, serialized via Jackson `write-dates-as-timestamps=false`).
- **Error Problem Details**: RFC 7807 format (`title`, `status`, `detail`, `timestamp`).

### 3.2 Endpoint & Role Authorization Matrix

| Endpoint | Method | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `/api/auth/setup/status` | `GET` | Public | Checks if system bootstrap is required |
| `/api/auth/setup` | `POST` | Public | Bootstraps first administrator (concurrency-locked) |
| `/api/auth/login` | `POST` | Public | Authenticates username/email and issues JWT |
| `/api/auth/register` | `POST` | Public | Registers `DRIVER` (with license) or `USER` account |
| `/api/dashboard` | `GET` | `ADMIN`, `DRIVER`, `USER` | Aggregated dashboard metrics (scoped by role) |
| `/api/locations` | `GET` | `ADMIN`, `DRIVER` | Lists active/all locations |
| `/api/locations` | `POST` | `ADMIN` | Creates new graph location node |
| `/api/locations/{id}` | `GET` | `ADMIN`, `DRIVER` | Retrieves location details |
| `/api/locations/{id}` | `PUT` | `ADMIN` | Updates location details |
| `/api/locations/{id}` | `DELETE`| `ADMIN` | Soft-deactivates location (checks dependencies) |
| `/api/locations/geocode` | `GET` | `ADMIN`, `USER` | Geocodes address via Nominatim |
| `/api/locations/{id}/approve` | `POST` | `ADMIN` | Approves pending location & links edges |
| `/api/edges` | `GET` | `ADMIN`, `DRIVER` | Lists directed road edges |
| `/api/edges` | `POST` | `ADMIN` | Creates directed edge between locations |
| `/api/edges/{id}` | `GET` | `ADMIN`, `DRIVER` | Retrieves edge details |
| `/api/edges/{id}` | `PUT` | `ADMIN` | Updates edge distance, time, or geometry |
| `/api/edges/{id}` | `DELETE`| `ADMIN` | Soft-deactivates road edge |
| `/api/edges/calculate-route` | `GET` | `ADMIN` | Queries OSRM for live distance/time/GeoJSON |
| `/api/drivers` | `GET` | `ADMIN`, `DRIVER` | Lists driver profiles |
| `/api/drivers` | `POST` | `ADMIN` | Creates driver profile |
| `/api/drivers/{id}` | `PUT` | `ADMIN` | Updates driver profile details |
| `/api/drivers/{id}` | `DELETE`| `ADMIN` | Soft-deactivates driver profile |
| `/api/vehicles` | `GET` | `ADMIN`, `DRIVER` | Lists fleet vehicles |
| `/api/vehicles` | `POST` | `ADMIN` | Creates vehicle asset |
| `/api/vehicles/{id}` | `PUT` | `ADMIN` | Updates vehicle asset details |
| `/api/vehicles/{id}` | `DELETE`| `ADMIN` | Soft-deactivates vehicle asset |
| `/api/packages` | `GET` | `ADMIN`, `DRIVER`, `USER` | Lists packages (USER sees only own packages) |
| `/api/packages` | `POST` | `ADMIN`, `USER` | Creates package (auto-links `customer_id`) |
| `/api/packages/{id}` | `GET` | `ADMIN`, `DRIVER`, `USER` | Retrieves package (USER ownership enforced) |
| `/api/packages/{id}` | `PUT` | `ADMIN` | Updates package attributes |
| `/api/packages/{id}` | `DELETE`| `ADMIN` | Soft-deactivates package |
| `/api/deliveries` | `GET` | `ADMIN`, `DRIVER`, `USER` | Lists deliveries (scoped by role ownership) |
| `/api/deliveries` | `POST` | `ADMIN` | Schedules delivery for a package |
| `/api/deliveries/{id}` | `GET` | `ADMIN`, `DRIVER`, `USER` | Retrieves delivery details (ownership enforced) |
| `/api/deliveries/{id}/assign` | `POST` | `ADMIN` | Assigns driver & vehicle (pessimistic lock) |
| `/api/deliveries/{id}/status` | `POST, PATCH` | `ADMIN`, `DRIVER` | Advances lifecycle state (Driver scoped) |
| `/api/deliveries/{id}/history`| `GET` | `ADMIN`, `DRIVER`, `USER` | Retrieves status audit history |
| `/api/deliveries/{id}/routes` | `GET` | `ADMIN`, `DRIVER` | Lists all route versions for delivery |
| `/api/deliveries/{id}/routes/current` | `GET` | `ADMIN`, `DRIVER` | Retrieves current active route |
| `/api/optimization` | `POST` | `ADMIN` | Calculates and persists Dijkstra route |
| `/api/routes/{id}` | `GET` | `ADMIN`, `DRIVER` | Retrieves route details with stops and edges |
| `/api/analytics` | `GET` | `ADMIN` | Aggregates business KPIs and trends |
| `/api/reports/deliveries/csv`| `GET` | `ADMIN` | Downloads delivery audit CSV |
| `/api/reports/routes/csv` | `GET` | `ADMIN` | Downloads route audit CSV |
| `/api/users` | `GET` | `ADMIN` | Lists all user accounts |
| `/api/users` | `POST` | `ADMIN` | Creates user account (`ADMIN`, `DRIVER`, `USER`) |
| `/api/users/{id}` | `GET` | `ADMIN`, `DRIVER` | Retrieves user profile by ID |
| `/api/users/profile` | `GET` | Authenticated | Retrieves current logged-in profile |
| `/api/users/profile/password`| `PUT` | Authenticated | Rotates password for current user |
| `/api/experiments/ddqn/latest`| `GET` | `ADMIN` | Retrieves latest DDQN benchmark results |
| `/api/experiments/ddqn/replan`| `POST` | `ADMIN` | Runs isolated DDQN dynamic replanning demo |

---

## 4. Authentication, Authorization & Security Architecture

### 4.1 Bootstrap Flow & Concurrency Synchronization
1. When the `users` table has zero administrators, `GET /api/auth/setup/status` returns `setupRequired = true`.
2. Initial administrator creation via `POST /api/auth/setup` executes inside a `READ_COMMITTED` transaction that acquires PostgreSQL transaction-level advisory lock `8844221199L`:
   ```sql
   SELECT pg_advisory_xact_lock(8844221199);
   ```
3. If an administrator already exists, the request is rejected with `409 Conflict`.
4. The bootstrap is strictly restricted to development and test databases (`packagedeliveryrouteoptimizer` and `route_optimizer_test_db`).

### 4.2 Stateless JWT Security
- **Token Format**: Bearer access tokens signed with HMAC-SHA256 (`jjwt 0.12.6`).
- **Lifetime**: 15 minutes (`900,000 ms`).
- **Payload**: Subject contains `username`. Roles are retrieved via `CustomUserDetailsService` on each request.
- **Passwords**: Hashed with BCrypt (10 rounds). Plaintext passwords are never stored, logged, or printed.

### 4.3 Three-Role Authorization Model
The platform supports exactly three roles enforced by Flyway migration `V7__remove_dispatcher_role.sql` with a database check constraint:
```sql
ALTER TABLE users ADD CONSTRAINT check_users_role CHECK (role IN ('ADMIN', 'DRIVER', 'USER'));
```

- **ADMIN**: Unrestricted administrative, master data, fleet, route optimization, audit, and reporting access.
- **DRIVER**: Scoped access. Can view assigned deliveries, view route maps for assigned deliveries, advance delivery status (`IN_PROGRESS`, `COMPLETED`, `FAILED`). Cannot cancel deliveries, access user management, alter master data, or trigger global route optimizations.
- **USER**: Customer access. Can register, create packages, view own packages (`customer_id == auth.id`), and track deliveries for own packages. Cannot view fleet assets, global routes, or other users' packages.

---

## 5. Production Dijkstra Routing Engine

The core production routing engine is implemented purely in Java (`DijkstraRouteOptimizationStrategy.java`) without external runtime routing dependencies.

### 5.1 Algorithm Operation
1. **Graph Construction**: Queries all active edges (`findByActiveTrue()`). Only edges connecting active source and destination locations are added to the adjacency list `Map<Long, List<LocationEdge>>`.
2. **Min-PriorityQueue Relaxation**: Tracks tentative shortest distance/time using `PriorityQueue<NodeDistance>`.
   - Under `SHORTEST_DISTANCE`, the edge weight is `edge.getDistance()` in kilometers.
   - Under `FASTEST_ROUTE`, the edge weight is `edge.getEstimatedTime()` in minutes.
3. **Predecessor Map**: Tracks optimal incoming edges for backwards path reconstruction from destination to source.
4. **Authoritative Travel Time**: The total estimated route time is computed by directly summing the authoritative `estimated_time` values stored on the traversed edges:
   $$\text{totalEstimatedTime} = \sum_{e \in \text{Path}} \text{edge.estimatedTime}$$
   When travel time is omitted during edge creation, the fallback formula is applied:
   $$\text{estimatedTime} = \frac{\text{distance}}{40.0} \times 60.0 \text{ minutes}$$
5. **Route Persistence & Versioning**:
   - Persists route in `routes` table with `algorithm_used = 'DIJKSTRA'`.
   - Snapshots individual stops in `route_stops` (SOURCE, WAYPOINT, DESTINATION) with 1-based order.
   - Snapshots individual edges in `route_edges` with distance, transit time, and `geometry_geojson`.
   - Updates `delivery.estimated_distance` and `delivery.estimated_time`.
   - Increments `version_number` and marks prior routes for that delivery as `is_current = false`.

---

## 6. OSRM Road Geometry & External Geocoding

### 6.1 Open Source Routing Machine (OSRM) Integration
- **Client**: `RoutingService.java` invokes public OSRM via Spring's `RestClient`:
  `GET http://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson`
- **Output Processing**:
  - Distance converted from meters to kilometers: `distanceKm = bestRoute.getDistance() / 1000.0`.
  - Duration converted from seconds to minutes: `durationMin = bestRoute.getDuration() / 60.0`.
  - GeoJSON `LineString` coordinate array serialized into `geometry_geojson` column (`TEXT`).
- **Persistence Pipeline**:
  - Stored in `location_edges.geometry_geojson`.
  - Copied into `route_edges.geometry_geojson` upon route persistence.
- **Frontend Visualization (`Map.jsx`)**:
  - Parses GeoJSON coordinate pairs `[lon, lat]` into Leaflet `[lat, lon]`.
  - Renders solid green polyline for segments with real road geometry.
  - Renders dashed amber polyline for graph connections lacking stored road geometry.
  - Displays geometry status indicator badge on the map.

### 6.2 OpenStreetMap Nominatim Geocoding
- **Client**: `GeocodingService.java` queries `https://nominatim.openstreetmap.org/search?q={address}&format=json&limit=1`.
- **Throttling & Fallback**: Enforces 1 req/sec rate limit. Returns fallback coordinates (`12.9716, 77.5946`) if Nominatim blocks requests or errors out during integration tests.
- **Customer Address Integration**: When users submit non-master addresses, `PackageService` creates pending `Location` records (`active = false`, `locationType = 'CUSTOMER'`). Admins approve and link these locations via `POST /api/locations/{id}/approve`.

---

## 7. Delivery Lifecycle State Machine

```
              ┌───────────────────────────┐
              │          CREATED          │
              └─────────────┬─────────────┘
                            │ (Admin Assigns Driver & Vehicle)
                            │ [Pessimistic DB Locks]
                            ▼
              ┌───────────────────────────┐
       ┌─────►│          ASSIGNED         │◄─────┐
       │      └─────────────┬─────────────┘      │
       │                    │ (Driver Starts)    │
       │                    ▼                    │
       │      ┌───────────────────────────┐      │
       │      │        IN_PROGRESS        │      │
       │      └──────┬──────────────┬─────┘      │
       │ (Cancel)    │ (Complete)   │ (Fail)     │ (Cancel)
       ▼             ▼              ▼            ▼
┌───────────┐ ┌───────────┐  ┌───────────┐ ┌───────────┐
│ CANCELLED │ │ COMPLETED │  │   FAILED  │ │ CANCELLED │
└───────────┘ └───────────┘  └───────────┘ └───────────┘
 [Terminal]    [Terminal]     [Terminal]    [Terminal]
```

### 7.1 State Machine Invariants
1. **Pessimistic Concurrency Locking**: `findByIdWithLock` (`PESSIMISTIC_WRITE`) locks Delivery, Driver, and Vehicle rows during assignment and status transitions.
2. **Assignment Atomicity**:
   - Delivery transitions to `ASSIGNED`.
   - Driver transitions from `AVAILABLE` to `ON_DELIVERY`.
   - Vehicle transitions from `AVAILABLE` to `IN_USE`.
   - Package transitions from `PENDING` to `IN_DELIVERY`.
3. **Completion**:
   - Delivery transitions to `COMPLETED`, recording `completed_at = CURRENT_TIMESTAMP`.
   - Package transitions to `DELIVERED`.
   - Driver and Vehicle are automatically released back to `AVAILABLE`.
4. **Cancellation & Failure**:
   - `CANCELLED`: Admin-only. Package reverts to `PENDING`. Driver and vehicle released to `AVAILABLE`.
   - `FAILED`: Driver or Admin. Package transitions to `FAILED`. Driver and vehicle released to `AVAILABLE`.
5. **Terminality**: States `COMPLETED`, `CANCELLED`, and `FAILED` are immutable. No further transitions are accepted.
6. **Audit Trail**: Every state transition inserts a record into `delivery_status_history` capturing `previous_status`, `new_status`, `changed_by_user_id`, and transition reasons.

---

## 8. Database Architecture & Schema Relationships

```
┌──────────────┐       1:1       ┌──────────────┐
│    users     ├────────────────►│   drivers    │
└──────┬───────┘                 └──────┬───────┘
       │ 1:N                            │ 1:N
       ▼                                ▼
┌──────────────┐       1:N       ┌──────────────┐
│   packages   │◄────────────────┤  deliveries  │
└──────┬───────┘                 └──────┬───────┘
       │ N:1                            │ 1:N
       ▼                                ▼
┌──────────────┐       1:N       ┌──────────────┐
│  locations   │◄────────────────┤    routes    │
└──────┬───────┘                 └──────┬───────┘
       │ 1:N                            │ 1:N
       ▼                                ▼
┌──────────────┐                 ┌──────────────┐
│location_edges│                 │ route_stops  │
└──────────────┘                 │ route_edges  │
                                 └──────────────┘
```

### 8.1 Table Specifications
1. **`users`**: User credentials and roles. Unique constraints on `username` and `email`. CHECK constraint on `role IN ('ADMIN', 'DRIVER', 'USER')`.
2. **`drivers`**: Fleet driver profiles. Foreign key to `users(id)` (`ON DELETE RESTRICT`). Unique license number. Status: `AVAILABLE`, `ON_DELIVERY`, `UNAVAILABLE`.
3. **`vehicles`**: Fleet vehicles. Unique `registration_number`. Check constraint `capacity > 0`. Status: `AVAILABLE`, `IN_USE`, `MAINTENANCE`, `OFFLINE`.
4. **`locations`**: Graph vertices. Name, coordinates (`latitude`, `longitude`), `city`, `address`, `location_type`.
5. **`location_edges`**: Directed road segments. `source_location_id`, `destination_location_id`, `distance`, `estimated_time`, `geometry_geojson`. Check constraint prevents self-loops (`source != destination`). Unique pair index.
6. **`packages`**: Deliverable items. Tracking number, sender/receiver details, weight, priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`), status (`PENDING`, `IN_DELIVERY`, `DELIVERED`, `FAILED`), foreign keys to source/destination locations and `customer_id` (users).
7. **`deliveries`**: Order fulfillment instances. Links package, source, destination, assigned driver, assigned vehicle. Status: `CREATED`, `ASSIGNED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `FAILED`.
8. **`delivery_status_history`**: Immutable lifecycle audit log. References `deliveries(id)` (`ON DELETE CASCADE`) and `users(id)`.
9. **`routes`**: Dijkstra-computed routes. References `deliveries(id)` (`ON DELETE SET NULL`), source, destination, `version_number`, `is_current`, `total_distance`, `estimated_time`, `algorithm_used`.
10. **`route_stops`**: Ordered sequence of stops along a route. Unique composite `(route_id, stop_order)`.
11. **`route_edges`**: Ordered sequence of road edges traversed by a route, including snapped `geometry_geojson`. Unique composite `(route_id, edge_order)`.
12. **`research_historical_routes`** & **`research_historical_tasks`**: Isolated tables supporting the DDQN research pipeline.

---

## 9. Isolated DDQN Research Module

The DDQN module (`com.routeoptimizer.research`) is an isolated research experiment implementing Double Deep Q-Networks for dynamic pickup and delivery route planning:

- **Algorithm**: Reinforcement learning extension based on Double DQN (DDQN) with Action-Prioritized Experience Replay (APER), TSM-TW candidate generation, and i-DeepRoute sequence modeling.
- **Dataset**: Amazon Last Mile Routing Research dataset (`research-data/package_data.json`, `route_data.json`, `actual_sequences.json`).
- **Isolation Guarantee**: The DDQN research logic does **not** replace or intercept the production Dijkstra routing engine. It does not alter production business tables.
- **Dijkstra Comparison Status**: The Amazon Last Mile dataset does not contain an authoritative physical road graph or road edge travel times; hence direct Dijkstra comparison metrics against that dataset are flagged as unavailable with documented justification.
- **Dynamic Replanning Demo (`DynamicReplanningDemo.jsx` / `/api/experiments/ddqn/replan`)**: Provides a sandbox simulation of dynamic route insertion for incoming orders at $t > 0$ using the trained research policy without writing to production delivery tables.
