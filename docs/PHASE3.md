# Phase 3 — 业务协议与下行增强

| 属性 | 值 |
|------|-----|
| 版本 | v1.2 |
| 状态 | **已交付（M18～M22）** |
| 前置 | [Phase 2](./PHASE2.md) 生产化能力已交付 |

---

## 里程碑状态

| ID | 内容 | 状态 |
|----|------|------|
| M18 | GB28181 SIP 解析、REGISTER/MESSAGE、MANSCDP+xml | ✅ |
| M19 | GB28181 下行：InviteStream、DeviceControl/Broadcast、Catalog | ✅ |
| M20 | 离线下行 Redis pending + 上线补发 | ✅ |
| M21 | 下行广播 Topic `omni.command.downlink.broadcast` | ✅ |
| M22 | 控制面 API + Nacos 配置拉取 | ✅ |

**第四期**：[PHASE4.md](./PHASE4.md)（gRPC 集群转发、一致性哈希）。

---

## 快速启动（第三期全功能）

```powershell
docker compose up -d
.\scripts\create-topics.ps1
.\scripts\create-downlink-topics.ps1 -NodeIds "local-8080"   # 与 omni.node-id 一致
mvn clean package -DskipTests
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar
```

依赖：`omni.session.redis-enabled=true` 时 Redis 需可达；离线下发与 pending 依赖 Redis。

---

## M18/M19：GB28181

- **端口**：5060（TCP），仅 `gb28181` 插件
- **上行**：`REGISTER` → `200 OK`；`MESSAGE`/`NOTIFY` + MANSCDP+xml → Kafka
- **下行**：经 `omni.command.downlink.{nodeId}`（或统一 Topic + Router）

| commandType | 行为 |
|-------------|------|
| `InviteStream` / `INVITE` | SIP INVITE + SDP（`streamUrl`、`mediaPort`） |
| `DeviceControl` / `Broadcast` | MESSAGE + Control（`streamUrl` / `rtspUrl`） |
| `Catalog` | MESSAGE + Catalog Query XML |
| 其他 + `payload.xml` | 原样 XML |

下行 Kafka 示例：

```json
{
  "messageId": "cmd-gb-001",
  "deviceId": "34020000002000000001",
  "protocol": "gb28181",
  "commandType": "InviteStream",
  "payload": { "streamUrl": "10.0.0.8", "mediaPort": 10002 },
  "timeoutMs": 10000
}
```

设计细节：[design/07-gb28181.md](./design/07-gb28181.md)。

---

## M20：离线下行补发

| 项 | 说明 |
|----|------|
| Redis Key | `omni:downlink:pending:{deviceId}`（List） |
| 前置 | `omni.session.redis-enabled=true` |
| 开关 | `omni.downlink.pending-enabled=true` |
| 离线消费 | `DownlinkConsumer` 返回 `queued_pending` |
| 上线 | `PendingDownlinkReplayService` 鉴权后自动 drain |

---

## M21：下行广播

| 项 | 说明 |
|----|------|
| Topic | `omni.command.downlink.broadcast` |
| 开关 | `omni.downlink.broadcast-enabled=true` |
| 消息体 | `CommandEnvelope` + 可选 `filterProtocol`、`deviceIds[]` |
| 范围 | 无 `deviceIds` 时对本机所有匹配协议会话广播 |

---

## M22：控制面与 Nacos

| 能力 | 说明 |
|------|------|
| 路由查询 | `GET /api/v1/devices/{deviceId}/route`（Redis 索引 + nodeId） |
| 本地会话 | `GET /api/v1/devices/{deviceId}/session`（本机 Channel 信息） |
| Nacos | `omni.nacos.enabled=true`，轮询 Open API 拉取 `omni-gateway.yaml` 并热更 |

第三期引入 `spring-boot-starter-web`（8080）。Actuator 写操作 `omnidrain` 需编译保留参数名（根 `pom.xml` 已设 `maven-compiler-plugin` 的 `parameters=true`；IDE 需 `-parameters` 或 Maven 构建）。

---

## 配置摘要

```yaml
omni:
  session:
    redis-enabled: true
  downlink:
    pending-enabled: true
    broadcast-enabled: true
    broadcast-topic: omni.command.downlink.broadcast
  nacos:
    enabled: false
    server-addr: 127.0.0.1:8848
    data-id: omni-gateway.yaml
  api:
    enabled: true
```

---

## 压测

| 脚本 | 用途 |
|------|------|
| [pt07_device_capacity.py](../tools/loadtest/pt07_device_capacity.py) | 长连接容量（渐进建连 + Prometheus 对照） |
| [pt03_downlink_routing.py](../tools/loadtest/pt03_downlink_routing.py) | 分节点下行路由 |

报告：[loadtest/BASELINE-REPORT.md](./loadtest/BASELINE-REPORT.md)

---

## 相关文档

- [PHASES.md](./PHASES.md)
- [SETUP.md](./SETUP.md)
- [design/07-gb28181.md](./design/07-gb28181.md)
- [design/01-downlink-channel.md](./design/01-downlink-channel.md)
