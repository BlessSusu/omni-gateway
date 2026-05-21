# Phase 2 — Production Readiness

## Milestones

| ID | Status | Deliverable |
|----|--------|-------------|
| M7 | Done | Redis `DistributedSessionIndex`, per-node downlink topic `omni.command.downlink.{nodeId}`, optional `DownlinkRouterConsumer` |
| M8 | Done | `SslContextFactory`, per-port TLS in pipeline, cert reload hook via config refresh |
| M9 | Done | `drainPort`, `NodeDrainService`, Actuator `omnidrain` / `omnilisteners`, L2 listener diff |
| M10 | Done | Micrometer OTel bridge, spans (`protocol.sniff`, `kafka.uplink.publish`, `downlink.dispatch`), Grafana/Prometheus templates |
| M11 | Done | PT-03/05/06 scripts, `BASELINE-REPORT` Phase 2 section |

## Quick start

```bash
docker compose up -d          # Kafka + Redis
.\scripts\create-downlink-topics.ps1 -NodeIds "local-8080"
mvn -pl omni-bootstrap spring-boot:run
```

## Configuration highlights

```yaml
omni:
  session.redis-enabled: true
  downlink.node-topic-pattern: "omni.command.downlink.{nodeId}"
  downlink.router-enabled: false   # true = bridge from unified topic
  tls.enabled: false               # enable + listener tls: true for 9443
```

## Downlink routing

- **Recommended (C1):** business publishes to `omni.command.downlink.{nodeId}` (lookup node from Redis/API).
- **Migration:** set `omni.downlink.router-enabled=true`; publish to `omni.command.downlink`; router forwards via Redis index.

## Operations

- `POST /actuator/omnidrain?timeoutSec=120` — node drain (stop accept, stop downlink consumer, wait sessions).
- `GET /actuator/omnilisteners` — ports, session counts, draining flags.
