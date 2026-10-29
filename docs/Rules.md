# Engineering Rules & Architectural Constraints — Package Delivery Route Optimizer

Authoritative operational rules, architectural constraints, and security standards for all developers, operators, and automated agents working on the Package Delivery Route Optimizer codebase.

---

## 1. Stack & Architecture Preservation

1. **Technology Stack Invariants**:
   - **Backend**: Java 21 LTS (`--release 21`), Spring Boot 3.5.3 (Spring Data JPA, Spring Security, Validation).
   - **Frontend**: React 18.3.1, Vite 5.2.12, Axios, React Router DOM v6, React Leaflet 1.9.4, Recharts, Lucide React icons.
   - **Database**: PostgreSQL 18.x with Flyway migration versioning.
   - **Do not introduce alternate backend frameworks**, ORMs, or routing engines without documented architectural approval.
2. **Zero Runtime Schema Generation**:
   - `spring.jpa.hibernate.ddl-auto` must remain strictly set to `validate`.
   - Hibernate is prohibited from creating, altering, or dropping tables at runtime.
   - All schema modifications must be executed via versioned, forward-only Flyway SQL migrations in `backend/src/main/resources/db/migration/`.

---

## 2. Server Runtime & Port Governance

1. **Strict Port Assignments**:
   - **Frontend**: Port `5173` (Vite dev server) with reverse proxy routing `/api` to `http://localhost:8080`.
   - **Backend**: Port `8080` (Spring Boot embedded Tomcat).
   - Changing port numbers in source configuration or creating unapproved secondary listener ports is prohibited.
2. **Prohibition of Duplicate Server Instances**:
   - Never launch secondary or duplicate background processes on ports 5173 or 8080.
   - Before launching development servers, verify no orphaned `node.exe` or `java.exe` processes hold those ports using `Get-NetTCPConnection` or `netstat`.
   - Use `backend/start-backend.ps1` and `backend/stop-backend.ps1` to cleanly cycle services.

---

## 3. Database Environments & Isolation Boundaries

1. **Dedicated Database Targets**:
   - **Development Database**: `packagedeliveryrouteoptimizer` (default user: `app_user`).
   - **Test Database**: `route_optimizer_test_db` (default user: `postgres`).
   - **Production Database**: Configured strictly via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) with zero plaintext defaults.
2. **Automatic Database Identity Verification**:
   - `DatabaseIdentityVerifier.java` must execute Pre-Flyway and Post-Startup. It must immediately abort startup with `IllegalStateException` if the active Spring profile does not match the connected database name.

---

## 4. Destructive Testing Safeguards & `TestDatabaseGuard`

1. **Mandatory Guard Execution**:
   - Any test class, script, or fixture that executes destructive operations (`TRUNCATE`, `DROP`, `DELETE FROM`, or `CASCADE`) **MUST** invoke `TestDatabaseGuard.verifyTestDatabase(jdbcTemplate)` before running any destructive statement:
     ```java
     TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
     ```
2. **Absolute Protection of Development Database**:
   - Never execute destructive tests, automated cleanup scripts, or integration tests against `packagedeliveryrouteoptimizer`.
   - The test profile (`@ActiveProfiles("test")`) must always target `route_optimizer_test_db`.
   - If `TestDatabaseGuard` detects connection to any database other than `route_optimizer_test_db`, it must throw a fatal exception and abort immediately.

---

## 5. Business Data Integrity & Prohibition of Fake Data

1. **No Fake Records in Development Database**:
   - Do **NOT** insert synthetic, dummy, mocking, or fake business records (e.g. test packages, simulated drivers, fake deliveries) into `packagedeliveryrouteoptimizer`.
   - The development database contains verified operational records and must remain untainted.
2. **No Uncontrolled Seed Data**:
   - Uncontrolled or automatic database seeders (`CommandLineRunner` data injection, mock SQL files) are strictly prohibited in the development and production runtime pipelines.
   - Master data must only be created through authorized REST endpoints by authenticated administrators.
3. **Prohibition of Database Resets**:
   - Dropping, resetting, or running `TRUNCATE` against the development database `packagedeliveryrouteoptimizer` is strictly prohibited.

---

## 6. Authentication, Ownership & Three-Role Authorization

1. **The Three-Role Authorization Model**:
   - Supported roles are strictly `ADMIN`, `DRIVER`, and `USER`.
   - Role `DISPATCHER` is deprecated and prohibited by database constraint (`check_users_role CHECK (role IN ('ADMIN', 'DRIVER', 'USER'))`).
   - Never re-introduce `DISPATCHER` in security configs, controllers, or database migrations.
2. **User Data Ownership Enforcement**:
   - Customers (`USER`) must only access packages and deliveries linked to their user ID (`customer_id == auth.id`).
   - Field drivers (`DRIVER`) must only view routes and advance deliveries assigned to their driver profile (`delivery.driver.user.id == auth.id`).
   - Drivers are strictly prohibited from cancelling deliveries or modifying master data.
3. **Stateless JWT Lifetimes**:
   - Access tokens must remain stateless with a 15-minute expiration (`app.jwt.expiration-ms=900000`).
   - Tokens must be transmitted exclusively via HTTP header: `Authorization: Bearer <accessToken>`.

---

## 7. Routing Protocol & Algorithm Roles

1. **Java Dijkstra as Sole Production Router**:
   - The deterministic Java Dijkstra algorithm (`DijkstraRouteOptimizationStrategy.java`) is the absolute, authoritative production routing engine.
   - Production dispatches must calculate paths strictly via Java Dijkstra.
2. **DDQN-PPDRP as an Isolated Research Module**:
   - The Double Deep Q-Network module (`com.routeoptimizer.research`) is strictly a research component for offline academic evaluation using the Amazon Last Mile dataset.
   - The DDQN research logic must **never** intercept, override, or replace the production Dijkstra router.
   - Research simulation endpoints (`/api/experiments/ddqn/replan`) must remain sandboxed and must never mutate production delivery records.

---

## 8. Confirmation Requirements for Destructive Operations

1. **Operator Confirmation Required**:
   - Any script, migration, or administrative command that deletes files, truncates tables, drops constraints, or performs bulk schema updates requires explicit human operator confirmation.
   - Automated scripts must fail-safe if interactive confirmation is bypassed without an explicit `--force` flag accompanied by safety validations (such as account existence checks).
2. **Preservation of Existing Code**:
   - Do not move, rename, or delete existing source code, scripts, or Flyway migrations without explicit instruction.
   - Diagnostic and maintenance utilities in `scripts/` must be preserved.

---

## 9. Credential Security & Zero Exposure

1. **Absolute Prohibition of Plaintext Credentials**:
   - **NEVER** write, commit, log, or expose passwords, API tokens, BCrypt hashes, connection strings with embedded passwords, or private keys in source code, documentation, Git commits, or console logs.
2. **Secure Credential Inputs**:
   - Credential rotation scripts (`Rotate-AdminPassword.ps1`, `db-update-password.ps1`) must read passwords securely using `Read-Host -AsSecureString` or environment variables (`NEW_ADMIN_PASSWORD`).
   - Plaintext passwords held in memory must be overwritten and cleared immediately after hashing.
3. **Scheduled Task Authentication**:
   - Automated maintenance and backup scripts (`backup-database.ps1`) must authenticate via restricted password files (`PGPASSFILE` at `C:\backups\pgpass.conf`) rather than hardcoded script parameters.

---

## 10. Summary Quick Reference

| Category | Golden Rule |
| :--- | :--- |
| **Ports** | Frontend: `5173`, Backend: `8080`. No duplicate runtimes. |
| **Databases** | Dev: `packagedeliveryrouteoptimizer`, Test: `route_optimizer_test_db`. |
| **Safety Guard** | `TestDatabaseGuard` required before any destructive test statement. |
| **Migrations** | Flyway exclusively (`classpath:db/migration`). `hibernate.ddl-auto=validate`. |
| **Roles** | Strictly `ADMIN`, `DRIVER`, `USER`. `DISPATCHER` is permanently prohibited. |
| **Production Router**| Pure Java Dijkstra. DDQN is strictly an offline research extension. |
| **Credentials** | Zero plaintext passwords or BCrypt hashes in source code, logs, or docs. |
