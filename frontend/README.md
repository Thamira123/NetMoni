# NetMoni frontend

React and TypeScript dashboard for the existing NetMoni Spring Boot API and monitoring agent.

Use Node.js 22.22.2+ or 24.15+ for the frontend and its test runner.

## Run locally

1. Start the backend and database using the repository's local development instructions.
2. In this directory, run:

   ```powershell
   npm install
   npm run dev
   ```

The Vite server runs at `http://localhost:5173` and proxies `/api` and `/ws` to `http://localhost:8080` by default. Set `VITE_DEV_API_PROXY_TARGET` in `.env.local` to change that target. For a separately hosted API, set `VITE_API_BASE_URL` (including `/api`) and `VITE_WS_BASE_URL` as needed. Configure the backend's `FRONTEND_ALLOWED_ORIGINS` and `WEBSOCKET_ALLOWED_ORIGIN` to match the deployed frontend origin.

Copy `.env.example` to `.env.local` to configure local values. Vite exposes only variables prefixed with `VITE_`; do not put secrets in this file.

## Features

- Sign in and account registration through the backend. Dashboard data is loaded from the API.
- Dashboard summary, device and agent inventory, detail pages, monitoring results, alert actions with the backend's INFO/WARNING/CRITICAL severities, reports, and CSV export.
- Device create, edit, and delete actions follow the backend's ADMIN/OPERATOR role checks.
- JWT-authenticated API requests, session expiry handling, loading/error states, and periodic refresh.

The API currently returns the latest 500 monitoring results per refresh. Reports calculate from those available results. Device, agent, and alert lists use 100-item API pages.

## Live updates integration note

The frontend sends its bearer token in the STOMP `CONNECT` frame, subscribes to the backend's documented topics, and falls back to a 30-second API refresh. The backend validates the token before accepting subscriptions and rejects client-published events. For deployment, set `WEBSOCKET_ALLOWED_ORIGIN` on the backend to the frontend's exact origin.

Agent responses currently expose the agent name, CIDR, version, status, and heartbeat; they do not include host/IP fields. Device records must already be mapped to an agent through the backend API.

## Checks

```powershell
npm test
npm run lint
npm run build
```

Frontend tests cover API bearer/error behavior, login, dashboard data loading, and the devices, agents, monitoring, and alert pages using mocked backend responses.
