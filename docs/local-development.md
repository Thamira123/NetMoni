# Run the complete NetMoni system locally

The agent, Spring Boot server, PostgreSQL database, and React dashboard use three connections:

```text
Agent --TCP :9001--> Spring Boot --JDBC :5432--> PostgreSQL
Browser --HTTP/WebSocket :5173--> Vite --proxy :8080--> Spring Boot
```

Keep PostgreSQL, the server, the frontend, and the agent running in separate terminals. Start them in this order.

## 1. Prepare PostgreSQL and an operator account

Start the PostgreSQL Windows service and create a database named `netmoni`. The default local database settings are `localhost:5432`, user `postgres`; the backend script prompts for that user's password.

The dashboard's device creation requires an ADMIN or OPERATOR account. For the first startup, open PowerShell in `server` and set a one-time bootstrap account before starting the backend:

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

Open the Vite URL, normally `http://localhost:5173`, and sign in using the bootstrap ADMIN account. Vite forwards `/api` requests and `/ws` WebSocket traffic to `http://localhost:8080`. The frontend uses the same REST API, JWT-authenticated STOMP connection, and database as the server.

## 3. Register the agent

Copy `agent/config/agent.properties.example` to `agent/config/agent.properties`. Set `agent.segment-cidr` to an IPv4 subnet on an active network interface that you administer. Keep the local default backend address `127.0.0.1:9001` when running the agent and server on the same computer. Leave `agent.device-ids` empty for this first start.

From the project root, build and start the agent (Maven must be installed):

```powershell
mvn -f agent/pom.xml package
java -jar agent/target/netmoni-agent-1.0.0.jar agent/config/agent.properties
```

The agent registers itself and sends heartbeats. It stores its generated ID under `agent/data`; keep that directory between restarts. Confirm the agent appears online in the dashboard's Agents view.

## 4. Add a monitored device and connect it to the agent

In the dashboard, open Devices, choose **Add device**, enter the device name, IPv4 address, type, and select the online agent. Save it. Open the device details and copy its Device ID.

Stop the agent with `Ctrl+C`. Edit the private `agent/config/agent.properties` file and set `agent.device-ids` to the device IP and copied UUID:

```properties
agent.device-ids=192.168.1.10=replace-with-the-device-uuid
```

Replace both example values with the actual device IP and Device ID. If monitoring more than one device, use comma-separated `IP=UUID` entries. Start the agent again with the command above. The agent sends checks over TCP, the server validates the agent/device association and writes results to PostgreSQL, and the dashboard receives live updates over WebSocket.

## Connection settings

- Server HTTP/WebSocket: `localhost:8080`
- Agent TCP listener: `127.0.0.1:9001` by default; for a remote agent, configure the server listener and firewall deliberately, and do not expose the unauthenticated plain TCP protocol to an untrusted network.
- PostgreSQL: `localhost:5432`, database `netmoni` by default.
- Frontend: `localhost:5173`; set `VITE_DEV_API_PROXY_TARGET` in `frontend/.env` only when the backend runs at a different address.
- Agent settings and SNMP credentials belong only in the ignored local `agent/config/agent.properties`, never in Git.

See [backend setup](backend-setup.md), [agent setup](../agent/README.md), and the [agent TCP protocol](agent-protocol.md) for details.
