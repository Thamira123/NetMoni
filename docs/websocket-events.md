# WebSocket events

Connect to `ws://localhost:8080/ws` using STOMP and subscribe to these topics:

- `/topic/agents`: `agent.online`, `agent.offline`
- `/topic/devices`: `device.status.changed`
- `/topic/monitoring`: `monitoring.result.created`
- `/topic/alerts`: `alert.created`, `alert.read`, `alert.resolved`
- `/topic/dashboard`: `dashboard.statistics.updated`

Event envelope: `{ "event": "monitoring.result.created", "timestamp": "<ISO-8601>", "data": { ... } }`. Event data is the same DTO shape returned by the corresponding REST resource. The built-in simple broker is for development; production deployments should use an authenticated, scalable broker and restrict allowed origins.
