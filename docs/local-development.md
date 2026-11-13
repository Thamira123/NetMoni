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

Use your own password; do not commit it or share it. After the first account is created, turn bootstrap off in that terminal or leave it disabled on subsequent starts. When `JWT_SECRET` is not set, the script creates a private local signing key at `server/.netmoni-jwt-secret` and reuses it after backend restarts, so existing browser sessions remain valid.

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

Copy `agent/config/agent.properties.example` to `agent/config/agent.properties`. Set `agent.segment-cidr` and `agent.allowed-cidrs` to IPv4 networks on active interfaces that you administer. The agent and server must run on the same host for the default backend address `127.0.0.1:9001`; for separate hosts, configure a reachable private address and a trusted firewall path. Leave `agent.device-ids` empty for this first start.

From the project root, build and start the agent (Maven must be installed):

```powershell
mvn -f agent/pom.xml package
java -jar agent/target/netmoni-agent-1.0.0.jar agent/config/agent.properties
```

The agent registers itself, reports its local networks, and sends heartbeats. It stores its generated ID under `agent/data`; keep that directory between restarts. In Network Discovery, an ADMIN must authorize each intended CIDR for the agent. The CIDR must also appear in the agent's local `agent.allowed-cidrs`. Confirm each interface shows `Authorized` and `Scan eligible` before starting a scan.

## 4. Confirm automatic registration and monitoring

No dashboard device entry or UUID copy is needed for devices the agent discovers. The agent automatically scans each locally configured authorized network, and you can start one or several eligible networks from Network Discovery. The agent sends reachable IPv4 addresses and hostnames to the server and receives database UUIDs. The server stores each `(agent, IP)` once and sends WebSocket updates; the dashboard shows the actual per-network scan progress and discovered inventory. The agent monitors enabled devices using those UUIDs. Devices start as `UNKNOWN` and change to `ONLINE` or `OFFLINE` after the first check. Operators and admins can pause or resume monitoring from Devices; agents apply the change on their next command poll.

The scan uses ICMP reachability and tries the configured `agent.tcp.ports` if ICMP is blocked. Only scan networks you administer. Each network is capped at 4096 usable IPv4 addresses. The agent repeats background discovery at `agent.discovery.interval-seconds` (default 300), so newly reachable devices appear on a later scan.

The optional `agent.device-ids` setting remains available for pre-existing devices. Its comma-separated `IP=UUID` entries are merged with automatically returned IDs. See [the wire protocol](agent-protocol.md) for the discovery request and acknowledgement.

## Connection settings

- Server HTTP/WebSocket: `localhost:8080`
- Agent TCP listener: `127.0.0.1:9001` by default; for a remote agent, configure the server listener and firewall deliberately, and do not expose the unauthenticated plain TCP protocol to an untrusted network.
- PostgreSQL: `localhost:5432`, database `netmoni` by default.
- Frontend: `localhost:5173`; set `VITE_DEV_API_PROXY_TARGET` in `frontend/.env` only when the backend runs at a different address.
- Agent settings and SNMP credentials belong only in the ignored local `agent/config/agent.properties`, never in Git.

See [backend setup](backend-setup.md), [agent setup](../agent/README.md), and the [agent TCP protocol](agent-protocol.md) for details.
