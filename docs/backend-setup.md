# Backend setup

Requirements: Java 21, PostgreSQL, and Maven (or the included `mvnw.cmd`). Create the `netmoni` PostgreSQL database, then from PowerShell in `NetMoni/server` run `./run-backend.ps1`. It finds the installed JDK, sets `JAVA_HOME` for this run, prompts for the database password without displaying it, and starts Spring Boot. To verify Java detection only, run `./run-backend.ps1 -CheckJavaOnly`. The service listens on HTTP 8080 and TCP 9001. The TCP listener defaults to loopback (`127.0.0.1`); configure the host deliberately before remote agent access.

If startup reports `password authentication failed for user "postgres"`, PostgreSQL is reachable but the supplied password does not match that database role. Reset the `postgres` role password in pgAdmin (Login/Group Roles → `postgres` → Properties → Definition), save it, then rerun `./run-backend.ps1` and enter that same new password at the hidden prompt. Do not put it in `application.properties` or commit it.

Environment variables:

- `DB_URL` (default `jdbc:postgresql://localhost:5432/netmoni`), `DB_USERNAME` (default `postgres`), `DB_PASSWORD` (required by your local PostgreSQL setup)
- `JWT_SECRET` (use a private random value at least 32 bytes long. `run-backend.ps1` creates a temporary random secret per run if this variable is unset; set it yourself if tokens should remain valid after a restart), `JWT_LIFETIME_SECONDS` (default 3600)
- Optional first ADMIN creation: `BOOTSTRAP_ADMIN_ENABLED=true`, `BOOTSTRAP_ADMIN_USERNAME`, and `BOOTSTRAP_ADMIN_PASSWORD` (at least 10 characters). Turn it off after the first account is created. Public signup only creates VIEWERs.
- `AGENT_HEARTBEAT_TIMEOUT_SECONDS` (default 90), `AGENT_STATUS_CHECK_INTERVAL_MS` (default 15000)
- `FRONTEND_ALLOWED_ORIGINS` (comma-separated exact REST API origins; default `http://localhost:5173`), `WEBSOCKET_ALLOWED_ORIGIN` (WebSocket origin; default `http://localhost:5173`)
- Email is off by default. To enable critical-alert mail set `EMAIL_ALERTS_ENABLED=true`, `ALERT_EMAIL_RECIPIENT`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, and `SMTP_STARTTLS`.

Register a viewer via `POST /api/auth/register`, then use the returned bearer token for authenticated requests. Public signup cannot create ADMIN or OPERATOR accounts; use the one-time bootstrap variables for the first ADMIN. Run database-independent service tests with `./mvnw test -Dtest='*Test'`. The application itself still requires PostgreSQL to start because JPA is configured for PostgreSQL.
