# Route Optimizer Backend

## Development Commands

The backend implements a strict single-instance guard using OS-level file locks. Do NOT attempt to run `mvn spring-boot:run` manually from multiple terminals, as this will fail cleanly to prevent port 8080 collisions.

The official development commands are:

**Start the backend:**
```powershell
.\start-backend.ps1
```
(This will reuse the existing instance if the backend is already running on port 8080).

**Stop the backend:**
```powershell
.\stop-backend.ps1
```
(This will gracefully terminate the running Spring Boot instance on port 8080).
