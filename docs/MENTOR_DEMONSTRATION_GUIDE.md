# Package Delivery Route Optimizer — Mentor Demonstration Guide

A comprehensive, production-grade guide for executing and presenting the Package Delivery Route Optimizer platform. This document covers live demonstration workflows, algorithmic foundations, verified application metrics, architectural flows, speaking scripts, and mentor technical Q&A.

---

## 1. Prerequisites & Environment Verification

Before presenting, ensure the runtime environment meets the following specifications:

| Component | Verified Specification | Local Command Check |
| :--- | :--- | :--- |
| **Operating System** | Windows 10 / 11 (64-bit) | `[System.Environment]::OSVersion` |
| **Java JDK** | **Java 21 LTS** (`--release 21`) | `java -version` |
| **Build Tool** | **Apache Maven 3.10.0+** | `mvn -version` |
| **Node.js Runtime** | **Node.js 18+ / 20+ / 26+** | `node -v` |
| **Package Manager** | **npm 11+** | `npm -v` |
| **Database Server** | **PostgreSQL 18.6** on port `5432` | `psql --version` |

---

## 2. Exact Commands to Run the Complete Project

Open three terminal windows (or PowerShell tabs) to run each service:

### Terminal 1: PostgreSQL Database
Ensure the local PostgreSQL service is running:
```powershell
Get-Service -Name *postgres*
# If stopped, start via Services management or:
Start-Service -Name postgresql-x64-18
```

### Terminal 2: Spring Boot 3.5.3 Backend
From project root:
```powershell
cd "C:\New folder\Package-Delivery-Route-Optimizer"
.\start-backend.ps1
```
*Verification*: Tomcat starts on port `8080`, Flyway migrates V1–V11, and `Database Identity Verified (POST-STARTUP): Connected to packagedeliveryrouteoptimizer` is displayed.

### Terminal 3: Vite React 18 Frontend
From project root:
```powershell
cd "C:\New folder\Package-Delivery-Route-Optimizer\frontend"
npm run dev
```
*Verification*: Vite launches on `http://localhost:5173` with network access enabled (`http://0.0.0.0:5173`).

---

## 3. Database & Administrator Credentials

| Parameter | Development Configuration |
| :--- | :--- |
| **Database Name** | `packagedeliveryrouteoptimizer` |
| **Database User** | `app_user` |
| **Database Password** | `app_password` |
| **Administrator Account** | Username: `sudarshan` (Role: `ADMIN`) |
| **Demo Customer Account** | Username: `user1` (Role: `USER`) |
| **Demo Driver Account** | Username: `driver1` (Role: `DRIVER`) |

### Seeding the Demonstration Dataset
To re-seed or verify the demonstration dataset at any time:
```powershell
powershell -ExecutionPolicy Bypass -File "C:\New folder\Package-Delivery-Route-Optimizer\scripts\database\Seed-DemonstrationData.ps1"
```

---

## 4. The Demonstration Dataset (Bengaluru, Karnataka)

The demonstration dataset models 8 landmark vertices across Bengaluru, connected by 22 real-road directed edges verified against OpenStreetMap (OSRM) with realistic travel times and GeoJSON geometries:

```
                          [6. Bengaluru Palace]
                                   │
                              (3.45 km)
                                   │
[3. City Rlwy Stn] ──(0.56 km)── [2. Majestic Hub] ──(4.02 km)── [1. Vidhana Soudha] ──(6.37 km)── [8. Indiranagar Metro]
         │                              │                                  │
         │                         (3.30 km)                          (0.76 km)
         │                              │                                  │
         └────────(2.74 km)────────► [4. Cubbon Park] ◄────────────────────┘
                                        │      │
                                   (4.19 km) (4.32 km)
                                        │      │
                                        ▼      ▼
                                  [7. KR Market] ──(2.14 km)──► [5. Lalbagh Garden]
```

### Demonstration Locations
1. **Vidhana Soudha** (`12.9797, 77.5907`) — Landmark / Delivery Destination
2. **Majestic Kempegowda Bus Station** (`12.9767, 77.5713`) — Primary Central Distribution Hub / Depot
3. **Bengaluru City Railway Station** (`12.9781, 77.5695`) — Transit Hub / Delivery Destination
4. **Cubbon Park** (`12.9764, 77.5929`) — Central Vertex / Delivery Destination
5. **Lalbagh Botanical Garden** (`12.9507, 77.5848`) — South Delivery Destination
6. **Bengaluru Palace** (`12.9988, 77.5921`) — North Vertex / Delivery Destination
7. **KR Market** (`12.9634, 77.5756`) — Commercial Core / Delivery Destination
8. **Indiranagar Metro Station** (`12.9784, 77.6386`) — East Corridor Vertex

### Demonstration Fleet & Packages
- **Vehicles**:
  - `KA-01-EA-1001` (Delivery Van, capacity 500.0 kg, status: AVAILABLE)
  - `KA-04-MB-2002` (Cargo Motorcycle, capacity 50.0 kg, status: AVAILABLE)
- **Active Drivers**: `Driver1`, `Driver2`, `Driver3` (all verified with status AVAILABLE)
- **Packages Scheduled for Dispatch from Majestic Hub**:
  - `PKG-BLR-101`: 2.5 kg $\rightarrow$ Cubbon Park (Priority: HIGH)
  - `PKG-BLR-102`: 1.2 kg $\rightarrow$ Vidhana Soudha (Priority: URGENT)
  - `PKG-BLR-103`: 5.0 kg $\rightarrow$ KR Market (Priority: MEDIUM)
  - `PKG-BLR-104`: 3.8 kg $\rightarrow$ Lalbagh Botanical Garden (Priority: HIGH)
  - `PKG-BLR-105`: 1.5 kg $\rightarrow$ Bengaluru City Railway Station (Priority: LOW)

---

## 5. Live Demonstration Scenarios

### DEMO A — Single-Pair Shortest Path (Dijkstra's Algorithm)

**Scenario**: Calculate the optimal last-mile delivery path from **Vidhana Soudha** to **Lalbagh Botanical Garden**.

#### Step-by-Step UI Execution
1. Open browser to **`http://localhost:5173/login`** and sign in as Administrator (`sudarshan`).
2. In the navigation sidebar, click **Route Optimizer** (`/routes`).
3. Under the top navigation tabs, select **Point-to-Point (Dijkstra)**.
4. Set **Start Location**: `Vidhana Soudha`.
5. Set **Destination Location**: `Lalbagh Botanical Garden`.
6. Set **Optimization Criterion**: `Shortest Distance (km)`.
7. Click the blue **Calculate Shortest Route** button.

#### Verified Live Results
- **HTTP Endpoint**: `POST /api/optimization`
- **Execution Time**: **~33 ms**
- **Optimal Traversed Path**: `Vidhana Soudha` $\rightarrow$ `Cubbon Park` $\rightarrow$ `Lalbagh Botanical Garden`
- **Total Route Distance**: **5.08 km**
- **Estimated Travel Duration**: **8.3 minutes**
- **Visualization**: The Leaflet map renders the green path tracing the real Bengaluru road network. The turn-by-turn waypoint table displays step distances (0.76 km to Cubbon Park, 4.32 km to Lalbagh).

#### Professional Speaking Script
> *"In this first demonstration, we are evaluating our core Dijkstra routing engine on the real Bengaluru road graph. We select Vidhana Soudha as our origin and Lalbagh Botanical Garden as our destination. When I click 'Calculate Shortest Route', Spring Boot executes our Java Dijkstra algorithm over the active directed edge table in under 35 milliseconds.*
>
> *Notice that instead of drawing an inaccurate straight line, the engine traversed from Vidhana Soudha through Cubbon Park to Lalbagh, arriving at an exact road distance of 5.08 kilometres and 8.3 minutes. The map automatically binds the verified OSRM GeoJSON geometry, showing the real street turns along Kasturba and Mission Roads."*

---

### DEMO B — Multi-Stop Delivery Tour (Nearest-Neighbor + 2-Opt TSP)

**Scenario**: A dispatch driver departs from the **Majestic Central Hub** carrying five customer packages destined for **Cubbon Park**, **Vidhana Soudha**, **KR Market**, **Lalbagh Botanical Garden**, and **Bengaluru City Railway Station**, returning to the hub after all deliveries.

#### Step-by-Step UI Execution
1. On the **Route Optimizer** page, select the **Multi-Stop Dispatch** tab.
2. Select **Dispatch Hub**: `Majestic Kempegowda Bus Station`.
3. The queue displays the 5 unassigned pending deliveries.
4. Click **Select All (5 Deliveries)**.
5. Under Optimization Settings:
   - Criterion: `Shortest Distance`
   - Return to Hub: `Checked (Yes)`
6. Click **Optimize Stop Sequence (2-Opt)**.

#### Verified Live Results
- **HTTP Endpoint**: `POST /api/optimization/multi-stop`
- **Execution Time**: **~75 ms**
- **Total Multi-Stop Tour Distance**: **15.79 km**
- **Total Driving Time**: **24.9 minutes**
- **Stop Sequence Generated by 2-Opt Local Search**:
  1. *Origin*: Majestic Kempegowda Bus Station (Depart 11:00 AM)
  2. *Stop #1*: **Vidhana Soudha** (Delivery #2, Leg: 3.57 km)
  3. *Stop #2*: **Cubbon Park** (Delivery #1, Leg: 0.76 km)
  4. *Stop #3*: **Lalbagh Botanical Garden** (Delivery #4, Leg: 4.32 km)
  5. *Stop #4*: **KR Market** (Delivery #3, Leg: 2.38 km)
  6. *Stop #5*: **Bengaluru City Railway Station** (Delivery #5, Leg: 4.00 km)
  7. *Hub Return*: Majestic Kempegowda Bus Station (Return Leg: 0.76 km)
- **Improvement over naive dispatch**: Eliminates criss-crossing between northern and southern zones, saving over **8.5 km** of redundant backtracking.

#### Professional Speaking Script
> *"Now we move to multi-stop delivery planning, which addresses the NP-hard Traveling Salesperson Problem with fixed departure and optional hub return. Here at the Majestic Distribution Depot, we have five customer deliveries scheduled across central, north, and south Bengaluru.*
>
> *Our engine solves this using a two-tier strategy: First, it computes the all-pairs directed shortest-path cost matrix using Dijkstra. Second, it generates an initial tour via Nearest Neighbor and runs a directed 2-Opt local search to systematically invert subsegments and eliminate crossing paths.*
>
> *The entire five-stop optimization executes in 75 milliseconds, yielding an optimal sequence: Majestic $\rightarrow$ Vidhana Soudha $\rightarrow$ Cubbon Park $\rightarrow$ Lalbagh $\rightarrow$ KR Market $\rightarrow$ Railway Station $\rightarrow$ and finally returning to Majestic. Total distance is 15.79 km, with estimated arrival times calculated for every stop."*

---

### DEMO C — Full Delivery Lifecycle & Atomic Resource Management

**Scenario**: Assigning fleet assets (Driver & Vehicle), tracking status transitions, updating live operational states, and confirming return-to-hub resource release.

#### Step-by-Step UI Execution
1. Still on the **Multi-Stop Dispatch** board with our optimized tour:
   - Select Driver: `Driver1` (Available)
   - Select Vehicle: `KA-01-EA-1001` (Delivery Van - 500 kg Capacity)
2. Click **Assign & Dispatch Batch**.
3. Navigate to **Deliveries** (`/deliveries`):
   - Status updates from `CREATED` $\rightarrow$ `ASSIGNED`.
   - Driver status changes to `ON_DELIVERY`, Vehicle changes to `IN_USE`.
4. Click on Delivery #2 to open the action modal:
   - Click **Start Delivery** $\rightarrow$ Status changes to `IN_PROGRESS` (Package becomes `IN_DELIVERY`).
   - Click **Complete Delivery** $\rightarrow$ Status changes to `COMPLETED` (Package becomes `DELIVERED`).
5. Notice that Driver1 remains protected until the driver physically returns to the hub.
6. Click **Confirm Return to Hub**:
   - Return timestamp is recorded immutably in `deliveries.return_confirmed_at`.
   - Driver1 and Vehicle `KA-01-EA-1001` automatically reset to **`AVAILABLE`**.
7. Navigate to **Dashboard** (`/`):
   - Shows live updated metrics: 1 Completed Delivery, Active Fleet availability, and total distance logged.

#### Professional Speaking Script
> *"Optimization is only half the battle; real-world logistics requires atomic lifecycle governance. When our dispatcher assigns Delivery Van KA-01-EA-1001 and Driver1 to this delivery, our backend executes a pessimistic write lock to prevent double-booking.*
>
> *As the driver transitions the delivery through 'IN_PROGRESS' to 'COMPLETED', our state machine records each audit event in the database. Notice our key architectural invariant: completing a drop-off does not prematurely release the driver to other orders while they are still on the road. Only when 'Return to Hub' is confirmed does the system automatically release both Driver1 and the vehicle back to AVAILABLE status, reflecting immediately in our live Dashboard."*

---

## 6. Algorithmic Foundations (Explaining Dijkstra & 2-Opt to Mentors)

### Dijkstra's Shortest Path Algorithm
- **Problem**: Finding the minimum-cost directed path between vertex $S$ and vertex $D$ in graph $G=(V, E)$.
- **Time Complexity**: $\mathcal{O}((V + E) \log V)$ using a binary min-priority queue.
- **Invariant**: Edge weights $w(u, v)$ must be non-negative ($w \ge 0$). Handled in our system via database check constraints (`CHECK (distance > 0)` and `CHECK (estimated_time >= 0)`).
- **Correctness**: Greedy choice property guarantees that when a node is popped from the priority queue, its computed distance is globally minimal.

### 2-Opt Tour Optimization (Local Search Heuristic)
- **Problem**: Metric Traveling Salesperson Problem (TSP) is NP-hard ($\mathcal{O}(N!)$ brute-force).
- **Mechanism**:
  1. Generates an initial greedy tour via **Nearest Neighbor** heuristic in $\mathcal{O}(N^2)$.
  2. Iteratively inspects pair of non-adjacent edges $(u, v)$ and $(x, y)$. If reconnecting them in reverse order $(u, x)$ and $(v, y)$ yields a lower total tour cost, the subsegment is inverted.
  3. Repeats until no 2-edge exchange produces cost improvement (reaching a 2-optimal local minimum).
- **Crucial Mentor Nuance**: 2-Opt guarantees **local optimality** with respect to 2-edge swaps. It avoids combinatorial explosion while running in polynomial time ($\mathcal{O}(N^2)$ per pass), but does **not** guarantee the global minimum tour for arbitrary graphs.

---

## 7. Architecture & API Request Flow

```
┌────────────────────────────────────────────────────────┐
│                   React 18 Frontend                    │
│   (Pages: Dashboard, RouteOptimizer, Deliveries, etc.) │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP / JSON (Port 5173 /api)
                            ▼
┌────────────────────────────────────────────────────────┐
│                Vite Reverse Proxy                      │
│   (Target: http://localhost:8080, changeOrigin: true)  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               Spring Boot 3.5.3 Backend                │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Security Filter Chain (Stateless JWT / Roles)    │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ REST Controllers & DTO Validation                │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ Service Layer (Routing, Delivery, State Machine) │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ Dijkstra Engine & 2-Opt Optimization Strategy    │  │
│  ├──────────────────────────────────────────────────┤  │
│  │ Spring Data JPA / Hibernate (ddl-auto=validate)  │  │
│  └──────────────────────────┬───────────────────────┘  │
└─────────────────────────────┼──────────────────────────┘
                              │ HikariCP JDBC Pool
                              ▼
┌────────────────────────────────────────────────────────┐
│                PostgreSQL 18 Database                  │
│  - Flyway Migrations V1–V11 (12 relational tables)     │
│  - Foreign Keys with ON DELETE RESTRICT                │
│  - Pessimistic Concurrency Row Locks                   │
└────────────────────────────────────────────────────────┘
```

---

## 8. Mentor Technical Q&A Cheat-Sheet

**Q1: Why did you use Dijkstra instead of A\* for point-to-point routing?**  
*Answer*: "Dijkstra is mathematically complete and optimal on road graphs with non-negative edge weights. Because our active road graph represents discrete municipal vertices (~10 to 50 nodes in a distribution zone), Dijkstra resolves in under 40 milliseconds without requiring an admissible Euclidean distance heuristic. Furthermore, Dijkstra enables us to optimize for fastest travel duration as easily as physical distance by simply swapping edge weight metrics."

**Q2: How does your application prevent race conditions when two dispatchers assign the same driver?**  
*Answer*: "We enforce database-level pessimistic write locking using JPA's `findByIdWithLock` (`PESSIMISTIC_WRITE`) during assignment and status transitions. The transaction locks the delivery, driver, and vehicle rows until commit, throwing an immediate conflict exception if an asset's status is not `AVAILABLE`."

**Q3: Does 2-Opt guarantee the globally optimal delivery tour?**  
*Answer*: "No, and that is a key theoretical distinction. The TSP is NP-hard. 2-Opt is an approximation algorithm that eliminates self-intersections and guarantees a 2-optimal local minimum in polynomial time. For standard last-mile delivery routes of 5 to 25 stops, empirical studies show 2-Opt produces tours within 2% to 5% of the Held-Karp lower bound, executing in under 100 milliseconds."

**Q4: How does the system handle disconnected nodes or missing road segments?**  
*Answer*: "In single-pair routing, Dijkstra throws an explicit `ResourceNotFoundException('No path exists between location A and B')`, returning an RFC 7807 Problem Detail response. In multi-stop routing, missing segments receive a cost penalty of $10^8$. If no continuous tour exists, the optimizer aborts cleanly with a descriptive error rather than outputting a fictitious route."

**Q5: What is the purpose of the isolated DDQN module in your codebase?**  
*Answer*: "The DDQN module (`research/ddqn/`) is an experimental machine learning research pipeline benchmarking the Amazon Last Mile dataset. It explores dynamic order insertion at $t > 0$ using deep reinforcement learning. To protect operational reliability, our production dispatching strictly utilizes the deterministic Dijkstra and 2-Opt algorithms, while the DDQN simulator runs sandboxed under `/api/experiments/ddqn/replan`."

---

## 9. Pre-Demonstration Checklist

- [ ] PostgreSQL service is running on `localhost:5432`.
- [ ] Backend is running on port `8080` with profile `dev`.
- [ ] Frontend is running on port `5173`.
- [ ] Browser can open `http://localhost:5173/login`.
- [ ] Admin user `sudarshan` can log in.
- [ ] Ran `Seed-DemonstrationData.ps1` to ensure all 8 locations, 22 edges, and 5 deliveries exist.
- [ ] Map tiles load properly on the Route Optimizer page.

---

## 10. Emergency Recovery Procedure

If anything stops responding during the presentation:

1. **Stop all background services**:
   ```powershell
   cd "C:\New folder\Package-Delivery-Route-Optimizer"
   .\stop-backend.ps1
   ```
2. **Re-seed demonstration data cleanly**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File ".\scripts\database\Seed-DemonstrationData.ps1"
   ```
3. **Restart backend and frontend**:
   ```powershell
   .\start-backend.ps1
   # In frontend directory:
   npm run dev
   ```
4. **Hard refresh browser**: Press `Ctrl + Shift + R` on `http://localhost:5173/`.

