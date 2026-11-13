# Run the complete NetMoni system locally

The agent, Spring Boot server, PostgreSQL database, and React dashboard use three connections:

```text
Agent --TCP :9001--> Spring Boot --JDBC :5432--> PostgreSQL
Browser --HTTP/WebSocket :5173--> Vite --proxy :8080--> Spring Boot
```

Keep PostgreSQL, the server, the frontend, and the agent running in separate terminals. Start them in this order.

## 1. Prepare PostgreSQL and an operator account

Start the PostgreSQL Windows service and create a database named `netmoni`. The default local database settings are `localhost:5432`, user `postgres`; the backend script prompts for that user's password.

Public dashboard signup creates a read-only VIEWER account. Automatic discovery works without manually creating devices. To manage devices manually or administer accounts, create a one-time ADMIN account before the first backend startup by opening PowerShell in `server` and setting:

```powershell
$env:BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:BOOTSTRAP_ADMIN_USERNAME = 'netmoni-admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = 'choose-a-private-password-of-10-or-more-characters'
./run-backend.ps1
```

Use your own password; do not commit it or share it. After the first account is created, turn bootstrap off in that terminal or leave it disabled on subsequent starts. The script generates a temporary JWT signing secret when `JWT_SECRET` is not set.

Wait for `Started NetMoniServerApplication` and `Agent TCP server listening on 127.0.0.1:9001` before continuing.

## 2. Start the dashboard

In another terminal, from the project root:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL, normally `http://localhost:5173`, and either register a VIEWER account or sign in using the bootstrap ADMIN account. Vite forwards `/api` requests and `/ws` WebSocket traffic to `http://localhost:8080`. The frontend uses the same REST API, JWT-authenticated STOMP connection, and database as the server.

## 3. Register the agent

Copy `agent/config/agent.properties.example` to `agent/config/agent.properties`. Set `agent.segment-cidr` to an IPv4 subnet on an active network interface that you administer. Keep the local default backend address `127.0.0.1:9001` when running the agent and server on the same computer. Leave `agent.device-ids` empty for this first start.

From the project root, build and start the agent (Maven must be installed):

```powershell
mvn -f agent/pom.xml package
java -jar agent/target/netmoni-agent-1.0.0.jar agent/config/agent.properties
```

The agent registers itself and sends heartbeats. It stores its generated ID under `agent/data`; keep that directory between restarts. Confirm the agent appears online in the dashboard's Agents view.

## 4. Confirm automatic registration and monitoring

No dashboard device entry or UUID copy is needed for devices the agent discovers. After it registers, the agent scans its configured subnet, sends reachable IPv4 addresses and hostnames to the server, and receives the database device UUIDs. The server stores each `(agent, IP)` once and sends a WebSocket event; the open dashboard refreshes its Devices view. The agent then submits ICMP/TCP results using those returned IDs. Devices start as `UNKNOWN` and change to `ONLINE` or `OFFLINE` after the first check.

The scan uses ICMP reachability and tries the configured `agent.tcp.ports` if ICMP is blocked. Set `agent.segment-cidr` to the correct IPv4 network on an active interface; only scan networks you administer. Discovery is capped at 4096 usable IPv4 addresses per scan. The agent repeats discovery at `agent.discovery.interval-seconds` (default 300), so newly reachable devices appear on a later scan.

The optional `agent.device-ids` setting remains available for pre-existing devices. Its comma-separated `IP=UUID` entries are merged with automatically returned IDs. See [the wire protocol](agent-protocol.md) for the discovery request and acknowledgement.

## Connection settings

- Server HTTP/WebSocket: `localhost:8080`
- Agent TCP listener: `127.0.0.1:9001` by default; for a remote agent, configure the server listener and firewall deliberately, and do not expose the unauthenticated plain TCP protocol to an untrusted network.
- PostgreSQL: `localhost:5432`, database `netmoni` by default.
- Frontend: `localhost:5173`; set `VITE_DEV_API_PROXY_TARGET` in `frontend/.env` only when the backend runs at a different address.
- Agent settings and SNMP credentials belong only in the ignored local `agent/config/agent.properties`, never in Git.

See [backend setup](backend-setup.md), [agent setup](../agent/README.md), and the [agent TCP protocol](agent-protocol.md) for details.
