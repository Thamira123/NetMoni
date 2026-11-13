# Local development

NetMoni is split into three runnable components. The frontend talks to the server over HTTP and WebSocket; the agent sends monitoring messages to the server over TCP.

```text
Browser → frontend (Vite :5173) → server HTTP/WebSocket (:8080)
Agent ──────────────────────────→ server TCP (:9001)
                                      ↓
                                  PostgreSQL (:5432)
```

## Start the server

Start PostgreSQL and create a database named `netmoni`. In PowerShell, open `server` and run:

```powershell
./run-backend.ps1
```

The script prompts for the local PostgreSQL password. See [backend setup](backend-setup.md) for account and environment settings. The agent TCP listener defaults to `127.0.0.1:9001`, which is suitable for local development.

## Start the frontend

In a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite address shown in the terminal (normally `http://localhost:5173`). Vite forwards `/api` requests and `/ws` WebSocket traffic to the server on port 8080. Sign in or register through the dashboard; the dashboard loads its device, agent, alert, and summary data from the server.

## Start an agent

Configure `agent/config/agent.properties` using `agent/config/agent.properties.example` as a template. Set the backend address and port, an authorized subnet, and device UUIDs that already exist in the server and belong to this agent. Then, in another terminal:

```powershell
cd agent
mvn package
java -jar target/netmoni-agent-1.0.0.jar config/agent.properties
```

The agent registers and sends heartbeat and monitoring results to the server. The server stores those results in PostgreSQL and publishes live dashboard updates over WebSocket. Device creation and assignment are currently done through the server API; the agent protocol does not create devices. See [agent setup and backend contract](../agent/README.md) and [agent protocol](agent-protocol.md).
