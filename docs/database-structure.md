# Database structure

The current JPA model has `agents`, `devices`, `monitoring_results`, `alerts`, and `app_users` tables. Devices reference their owning agent; monitoring results reference both device and agent. Indexes cover device/agent result timelines, device status, and alert deduplication/status. PostgreSQL schema creation currently uses Hibernate `ddl-auto=update` for development. Use a versioned migration tool before production and review any schema changes before applying them to persistent data.

Monitoring-result queries are capped at 500 rows for date-range requests and dashboard recent items are capped at 20. Agent, device, and alert list APIs are paginated (maximum page size 100). The by-agent device list currently returns all matching devices.
