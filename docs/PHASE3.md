# Phase 3 — 业务协议与下行增强

| 属性 | 值 |
|------|-----|
| 版本 | v1.1 |
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

## M19：GB28181 下行

`commandType` 支持：

| commandType | 行为 |
|-------------|------|
| `InviteStream` / `INVITE` | SIP INVITE + SDP（`streamUrl`、`mediaPort`） |
| `DeviceControl` / `Broadcast` | MESSAGE + MANSCDP Control（`streamUrl` / `rtspUrl`） |
| `Catalog` | MESSAGE + Catalog Query XML |
| 其他 + `payload.xml` | 原样 XML 下发 |

---

## M20：离线下行补发

- Redis 列表 `omni:downlink:pending:{deviceId}`（需 `omni.session.redis-enabled=true`）
- 配置：`omni.downlink.pending-enabled=true`
- 离线消费：result `queued_pending`
- 设备鉴权上线：`PendingDownlinkReplayService` 自动 drain 并下发

---

## M21：下行广播

- Topic：`omni.command.downlink.broadcast`
- 配置：`omni.downlink.broadcast-enabled=true`
- 消息体：标准 `CommandEnvelope` + 可选 `filterProtocol`、`deviceIds[]`
- 无 `deviceIds` 时对本机所有匹配协议会话广播

---

## M22：控制面与 Nacos

| 能力 | 路径 / 配置 |
|------|-------------|
| 路由查询 | `GET /api/v1/devices/{deviceId}/route` |
| 本地会话 | `GET /api/v1/devices/{deviceId}/session` |
| Nacos 拉取 | `omni.nacos.enabled=true`，轮询 Open API 并热更 |

---

## 配置摘要

```yaml
omni:
  downlink:
    pending-enabled: true      # 需 redis-enabled
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

## 相关文档

- [PHASES.md](./PHASES.md)
- [design/07-gb28181.md](./design/07-gb28181.md)
- [SETUP.md](./SETUP.md)
