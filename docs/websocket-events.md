# WebSocket events

Connect to `ws://localhost:8080/ws` using STOMP and subscribe to these topics. The browser's HTTP WebSocket handshake is permitted without a custom header; the STOMP `CONNECT` frame must include `Authorization: Bearer <JWT>` in its native headers. The server validates the token before accepting subscriptions and attaches the authenticated principal to the STOMP session. Missing, invalid, or expired tokens are rejected. Clients may subscribe to `/topic/` destinations but cannot publish events to the broker.

- `/topic/agents`: `agent.online`, `agent.offline`
- `/topic/devices`: `device.status.changed`
- `/topic/monitoring`: `monitoring.result.created`
- `/topic/alerts`: `alert.created`, `alert.read`, `alert.resolved`
- `/topic/dashboard`: `dashboard.statistics.updated`
- `/topic/discovery`: `discovery.started`, `discovery.progress`, `discovery.completed`, `discovery.failed`
- `/topic/networks`: `network.discovered`, `network.updated`

Event envelope: `{ "event": "monitoring.result.created", "timestamp": "<ISO-8601>", "data": { ... } }`. Event data is the same DTO shape returned by the corresponding REST resource. The built-in simple broker is for development; production deployments should use an authenticated, scalable broker and restrict allowed origins. Set `WEBSOCKET_ALLOWED_ORIGIN` to the exact frontend origin outside local development.
