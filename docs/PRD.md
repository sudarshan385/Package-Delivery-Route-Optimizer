# Product Requirements Document (PRD) — Package Delivery Route Optimizer

Authoritative product specifications, user personas, role permissions, functional requirements, and verifiable acceptance criteria for the Package Delivery Route Optimizer platform.

---

## 1. Product Objectives & Scope

### 1.1 Purpose
The Package Delivery Route Optimizer is designed to eliminate operational friction and resource allocation conflicts in last-mile package delivery logistics. The platform couples deterministic, graph-based shortest path route optimization with an atomic, state-synchronized delivery lifecycle management system.

### 1.2 Core Objectives
1. **Deterministic Route Optimization**: Compute optimal driving paths across directed road network graphs using Dijkstra's algorithm, supporting both minimum distance (km) and minimum transit time (minutes) criteria.
2. **Real Road Geometry Visualization**: Incorporate real-world OSRM road trajectories to provide drivers and dispatchers with accurate geographic curves rather than straight-line approximations.
3. **Atomic State Synchronization**: Guarantee that state transitions across deliveries, packages, drivers, and vehicles execute atomically under concurrency-safe row-level locking.
4. **Three-Role Access Control**: Enforce strict separation of duties between System Administrators (`ADMIN`), Field Drivers (`DRIVER`), and Customers (`USER`).
5. **Authoritative Operational Auditing**: Provide immutable delivery status history, real-time KPI metrics, and exportable CSV audit logs.
6. **Isolated Reinforcement Learning Research**: Provide a testbed for Double Deep Q-Network (DDQN-PPDRP) research on historical Amazon Last Mile datasets without interfering with production routing.

---

## 2. User Roles & Permission Matrix

The platform strictly enforces three operational roles (`ADMIN`, `DRIVER`, `USER`) as mandated by Flyway migration `V7__remove_dispatcher_role.sql`. The historical `DISPATCHER` role has been deprecated and permanently migrated to `ADMIN`.

### 2.1 Role Definitions
- **ADMIN (System Administrator / Logistics Manager)**: Full operational authority over users, road network nodes, edges, fleet personnel, vehicles, customer location approvals, global route optimizations, business analytics, and audit report generation.
- **DRIVER (Fleet Delivery Personnel)**: Field operational access scoped strictly to deliveries assigned to the driver. Can view assigned delivery details, inspect Dijkstra turn-by-turn route maps, advance delivery status to `IN_PROGRESS`, and mark deliveries `COMPLETED` or `FAILED`.
- **USER (Customer / Client)**: Self-service access to register accounts, submit package delivery requests (using standard locations or geocoded street addresses), and track the real-time fulfillment status of their own packages.

### 2.2 Section 44 Permission Matrix

| Operation | ADMIN | DRIVER | USER | Security Constraint / Rule |
| :--- | :---: | :---: | :---: | :--- |
| **1. Create User Accounts** | YES | NO | NO | `hasRole('ADMIN')` |
| **2. Manage User Roles** | YES | NO | NO | `hasRole('ADMIN')` |
| **3. View Dashboard** | YES | YES | YES | Scoped: Admin sees all; Driver/User see own records |
| **4. Manage Locations (Vertices)** | YES | NO | NO | `hasRole('ADMIN')` |
| **5. Manage Road Edges** | YES | NO | NO | `hasRole('ADMIN')` |
| **6. Geocode Addresses** | YES | NO | YES | `hasAnyRole('ADMIN', 'USER')` |
| **7. Approve Pending Locations** | YES | NO | NO | `hasRole('ADMIN')` |
| **8. Manage Drivers** | YES | NO | NO | `hasRole('ADMIN')` |
| **9. Manage Vehicles** | YES | NO | NO | `hasRole('ADMIN')` |
| **10. Create Packages** | YES | NO | YES | Admin or Customer; auto-links `customer_id` |
| **11. View Packages** | YES | YES | YES | Scoped: User restricted to `customer_id == auth.id` |
| **12. Update/Deactivate Packages** | YES | NO | NO | `hasRole('ADMIN')` |
| **13. Create Deliveries** | YES | NO | NO | `hasRole('ADMIN')` |
| **14. Assign Driver & Vehicle** | YES | NO | NO | `hasRole('ADMIN')` (Pessimistic lock) |
| **15. Advance Delivery: In Progress** | YES | YES | NO | Admin or Assigned Driver (`driver.user_id == auth.id`) |
| **16. Advance Delivery: Completed** | YES | YES | NO | Admin or Assigned Driver |
| **17. Advance Delivery: Failed** | YES | YES | NO | Admin or Assigned Driver |
| **18. Cancel Delivery** | YES | NO | NO | `hasRole('ADMIN')` (Drivers cannot cancel) |
| **19. Calculate/Persist Routes** | YES | NO | NO | `hasRole('ADMIN')` |
| **20. View Assigned Routes** | YES | YES | NO | Admin sees all; Driver sees only assigned delivery routes |
| **21. View Analytics & KPIs** | YES | NO | NO | `hasRole('ADMIN')` |
| **22. Export Audit CSV Reports** | YES | NO | NO | `hasRole('ADMIN')` |
| **23. Run DDQN Research Demo** | YES | NO | NO | `hasRole('ADMIN')` |
| **24. Rotate Own Password** | YES | YES | YES | Authenticated users via current password verification |

---

## 3. Functional Requirements

### 3.1 Authentication & System Bootstrap
- **FR-AUTH-1 (System Bootstrap)**: When the `users` table contains zero administrators, `GET /api/auth/setup/status` must return `setupRequired = true`. `POST /api/auth/setup` creates the initial administrator using PostgreSQL advisory transaction lock `8844221199L` to prevent duplicate accounts. Once configured, subsequent setup calls must return `409 Conflict`.
- **FR-AUTH-2 (User Authentication)**: `POST /api/auth/login` must accept either a valid `username` or registered `email` along with password, returning an HMAC-SHA256 Bearer JWT token with a 15-minute validity (`900,000 ms`).
- **FR-AUTH-3 (Public Registration)**: `POST /api/auth/register` must allow self-registration for `USER` and `DRIVER` roles. If `DRIVER` is selected, an authoritative `licenseNumber` is strictly mandatory. Administrator registration via this endpoint is prohibited (`403 Forbidden`).
- **FR-AUTH-4 (Self-Service Password Rotation)**: Authenticated users can rotate their password via `PUT /api/users/profile/password`. The system must verify the existing password before applying the new BCrypt hash.

### 3.2 Road Network Topology & Geocoding Management
- **FR-GEO-1 (Location Management)**: Admins can create, view, update, and soft-deactivate locations (`locations`). Deactivation must be rejected with `409 Conflict` if the location is currently referenced by active packages (`PENDING`, `IN_DELIVERY`) or active deliveries (`CREATED`, `ASSIGNED`, `IN_PROGRESS`).
- **FR-GEO-2 (Road Edge Management)**: Admins can define directed road segments (`location_edges`) with physical distance (km) and estimated transit time (minutes). Self-loops (`source == destination`) and duplicate directed pairs are prohibited by database constraints.
- **FR-GEO-3 (OSRM Live Routing)**: `GET /api/edges/calculate-route` queries the Open Source Routing Machine (`router.project-osrm.org`) to automatically compute driving distance, transit duration, and GeoJSON `LineString` road curves between coordinate pairs.
- **FR-GEO-4 (Forward Geocoding)**: `GET /api/locations/geocode` queries OpenStreetMap Nominatim with rate limiting (1 req/sec) to resolve street addresses into latitude and longitude coordinates.
- **FR-GEO-5 (Admin Approval Workflow)**: When customers enter custom addresses during package creation, the system creates pending location nodes (`active = false`, `locationType = 'CUSTOMER'`). Admins approve and integrate these nodes into the active graph via `POST /api/locations/{id}/approve`, creating directional edges to existing hubs with verified OSRM geometry.

### 3.3 Fleet Asset Management
- **FR-FLT-1 (Driver Management)**: Admins can register and manage drivers with unique license numbers, contact phone, and availability status (`AVAILABLE`, `ON_DELIVERY`, `UNAVAILABLE`). Deactivation must be confirmed via modal dialog.
- **FR-FLT-2 (Vehicle Fleet)**: Admins can register vehicles with registration plate numbers, vehicle type (`BIKE`, `CAR`, `VAN`, `TRUCK`), payload capacity (kg, must be $> 0$), and status (`AVAILABLE`, `IN_USE`, `MAINTENANCE`, `OFFLINE`).

### 3.4 Package Management & Customer Ownership
- **FR-PKG-1 (Package Creation)**: Admins and Customers can create packages specifying sender, receiver, weight, priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`), and either pre-existing location IDs or city/pickup/delivery addresses.
- **FR-PKG-2 (Customer Scoping)**: When a package is created by a `USER`, the system automatically attaches `customer_id = currentUser.id`. Customer queries (`GET /api/packages`, `GET /api/packages/{id}`) return only packages owned by the authenticated customer. Attempting to access another user's package returns `404 Not Found`.
- **FR-PKG-3 (Package Immutability)**: Once a package transitions to `IN_DELIVERY` or `DELIVERED`, updates to its source location, destination location, or weight are strictly rejected with `409 Conflict`.

### 3.5 Delivery Lifecycle State Machine & Resource Locking
- **FR-DEL-1 (Delivery Scheduling)**: Admins schedule deliveries for packages in `PENDING` status. The delivery is created in `CREATED` status.
- **FR-DEL-2 (Atomic Resource Assignment)**: Admins assign a driver and vehicle to a delivery in `CREATED` status via `POST /api/deliveries/{id}/assign`:
  - Executes with pessimistic database row locks (`PESSIMISTIC_WRITE`) on Delivery, Driver, and Vehicle.
  - Driver must be `AVAILABLE` and active; transitions to `ON_DELIVERY`.
  - Vehicle must be `AVAILABLE` and active; transitions to `IN_USE`.
  - Package transitions from `PENDING` to `IN_DELIVERY`.
  - Delivery transitions from `CREATED` to `ASSIGNED`.
- **FR-DEL-3 (Status Progression)**:
  - `ASSIGNED` $\rightarrow$ `IN_PROGRESS`: Triggered by assigned driver or admin when transit begins.
  - `IN_PROGRESS` $\rightarrow$ `COMPLETED`: Triggered by driver or admin upon delivery. Stamps `completed_at`, marks package `DELIVERED`, and automatically releases driver and vehicle back to `AVAILABLE`.
  - `ASSIGNED` / `IN_PROGRESS` $\rightarrow$ `CANCELLED`: Admin-only. Reverts package to `PENDING` and releases driver and vehicle to `AVAILABLE`.
  - `IN_PROGRESS` $\rightarrow$ `FAILED`: Driver or Admin. Marks package `FAILED` and releases driver and vehicle to `AVAILABLE`.
- **FR-DEL-4 (Terminal State Protection)**: Deliveries in `COMPLETED`, `CANCELLED`, or `FAILED` are terminal. No further state transitions are allowed (`409 Conflict`).
- **FR-DEL-5 (Audit Trail)**: Every transition automatically creates an immutable audit row in `delivery_status_history` capturing timestamps, user ID, previous status, new status, and reason.

### 3.6 Production Route Optimization Engine
- **FR-OPT-1 (Dijkstra Calculation)**: Pure Java Dijkstra algorithm determines the optimal path across active graph edges:
  - Criteria: `SHORTEST_DISTANCE` (minimizing kilometers) or `FASTEST_ROUTE` (minimizing travel time).
  - Authoritative travel time: Sum of exact `estimated_time` values on traversed edges, with `(distance / 40.0) * 60.0` minutes fallback when omitted.
- **FR-OPT-2 (Route Persistence & Versioning)**: When associated with an active delivery (`POST /api/optimization` with `deliveryId`):
  - Creates a new `routes` record with `algorithm_used = 'DIJKSTRA'` and `is_current = true`.
  - Deactivates previous current routes for that delivery (`is_current = false`) and increments `version_number`.
  - Persists ordered stop milestones in `route_stops` (SOURCE, WAYPOINT, DESTINATION).
  - Persists ordered edge snapshots in `route_edges` copying `geometry_geojson`.
  - Synchronizes `estimated_distance` and `estimated_time` onto the delivery entity.
- **FR-OPT-3 (Driver Route Scoping)**: Drivers can only view route paths and stop milestones for deliveries assigned to their driver profile.

### 3.7 Operational Dashboards, Analytics & CSV Reports
- **FR-RPT-1 (Role-Scoped Dashboard)**: `GET /api/dashboard` provides real-time counts. Admin view aggregates system-wide statistics; Driver view aggregates assigned delivery counts; User view aggregates customer order statuses.
- **FR-RPT-2 (Business Analytics)**: `GET /api/analytics` computes delivery success rate, average completion time, and fleet utilization across optional date filters (`startDate`, `endDate`).
- **FR-RPT-3 (Authoritative CSV Exports)**:
  - `GET /api/reports/deliveries/csv`: Streams all delivery fulfillment records as UTF-8 CSV.
  - `GET /api/reports/routes/csv`: Streams all calculated route records, stop counts, and distances as UTF-8 CSV.

### 3.8 Isolated DDQN Research Module
- **FR-AI-1 (Model & Dataset Isolation)**: The DDQN-PPDRP research module (`research/`) operates on historical Amazon Last Mile datasets (`research-data/`) and must never replace or intercept the production Dijkstra routing engine.
- **FR-AI-2 (Benchmark Results Display)**: `GET /api/experiments/ddqn/latest` serves evaluation metrics (CMR, Edit Distance, training updates) rendered on `AIOptimization.jsx`.
- **FR-AI-3 (Dynamic Replanning Demonstration)**: `POST /api/experiments/ddqn/replan` allows operators to simulate dynamic package insertion at $t > 0$ using the isolated research policy without modifying production tables.

---

## 4. Non-Functional Requirements (NFR)

1. **Security & Data Protection**:
   - Zero credentials or password hashes stored in logs or source files.
   - All passwords hashed via BCrypt (10 rounds).
   - Bearer JWT tokens expire after 15 minutes.
   - Strict CORS configuration whitelisting `http://localhost:5173`.
2. **Concurrency & Locking**:
   - Pessimistic write locks on Delivery, Driver, and Vehicle rows during dispatch to eliminate double-booking race conditions.
   - Database advisory lock on initial admin creation to prevent duplicate bootstraps.
3. **Database Integrity**:
   - Zero runtime schema modification (`spring.jpa.hibernate.ddl-auto=validate`).
   - All schema changes executed strictly via versioned Flyway migrations (`V1` - `V7`).
   - Foreign key integrity protected by `ON DELETE RESTRICT` on business records.
4. **Environment Isolation**:
   - Programmatic verification (`DatabaseIdentityVerifier`, `TestDatabaseGuard`) guarantees destructive tests never execute against `packagedeliveryrouteoptimizer`.

---

## 5. Verifiable Acceptance Criteria

| Criteria ID | Requirement | Test / Verification Method | Acceptance Standard |
| :--- | :--- | :--- | :--- |
| **AC-AUTH-01** | Initial Admin Bootstrap | `InitialAdminSetupIntegrationTest.java` | Creates admin under advisory lock; second attempt returns 409 Conflict. |
| **AC-AUTH-02** | JWT Token Expiration | `AuthenticationAndJwtIntegrationTest.java` | Token contains subject and valid signature; rejected after expiry. |
| **AC-AUTH-03** | Password Rotation | `AdminUserRecoveryTest.java` | Password updated without altering user ID, role, or creating duplicates. Bad current password returns 401. |
| **AC-ROLE-01** | Section 44 Permissions | `Section44PermissionMatrixIntegrationTest.java` | All 20 permission matrix endpoints return 200 for allowed roles and 403 for unauthorized roles. |
| **AC-ROLE-02** | Dispatcher Deprecation | `Section44PermissionMatrixIntegrationTest.java` | Token with role `DISPATCHER` is rejected; check constraint rejects `DISPATCHER` inserts. |
| **AC-GEO-01** | Customer Geocoding | `AdminApprovalWorkflowIntegrationTest.java` | Geocoding resolves coordinate points; pending location created with `active = false`. |
| **AC-GEO-02** | Location Approval Workflow | `AdminApprovalWorkflowIntegrationTest.java` | Admin approves pending location, linking incoming/outgoing edges with geometry; location becomes `active = true`. |
| **AC-GEO-03** | OSRM Geometry Persistence | `DijkstraAndRouteOptimizationIntegrationTest.java` | Valid GeoJSON `LineString` persisted on `location_edges` and snapshotted onto `route_edges`. |
| **AC-DELIV-01** | Atomic Assignment Lock | `DeliveryAssignmentAndConcurrencyIntegrationTest.java` | Concurrent assignment attempts allocate vehicle/driver to exactly one delivery; second returns 409. |
| **AC-DELIV-02** | State Machine Transitions | `DeliveryLifecycleAndStateMachineIntegrationTest.java` | Follows CREATED $\rightarrow$ ASSIGNED $\rightarrow$ IN_PROGRESS $\rightarrow$ COMPLETED; terminal states block transitions. |
| **AC-DELIV-03** | Resource Release | `DeliveryLifecycleAndStateMachineIntegrationTest.java` | Driver and vehicle automatically revert to `AVAILABLE` upon completion, cancellation, or failure. |
| **AC-OWN-01** | Customer Data Isolation | `UserDeliveryOwnershipIntegrationTest.java` | Customer can only view own packages and deliveries; foreign IDs return 404/403. |
| **AC-ROUT-01** | Dijkstra Shortest Path | `DijkstraAndRouteOptimizationIntegrationTest.java` | Computes correct graph shortest path; sums authoritative edge transit times. |
| **AC-ROUT-02** | Route Versioning | `DijkstraAndRouteOptimizationIntegrationTest.java` | Re-optimizing increments version number and sets previous route to `is_current = false`. |
| **AC-SAFE-01** | Database Identity Guard | `TestDatabaseGuardSafetyTest.java` | Attempting destructive operations on non-test database immediately throws `IllegalStateException`. |
| **AC-AI-01** | DDQN Isolation | `ResearchModuleIntegrationTest.java` | Research tables migrate independently; research API does not alter production delivery records. |
| **AC-UI-01** | Frontend State Tests | `frontend/tests/frontend.test.js` | 6 automated tests pass for Bearer formatting, role hierarchy, lifecycle transitions, and form validation. |
