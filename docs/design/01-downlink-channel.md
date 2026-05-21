# 详细设计 01：下行通道（Downlink）

| 属性 | 值 |
|------|-----|
| 状态 | **已实现**（Phase 1～3） |
| 依赖模块 | omni-core, omni-network, omni-bootstrap |
| 关联设计 | [02-horizontal-scaling](./02-horizontal-scaling.md) |

**第三期扩展**（已实现）：离线下发 `PendingDownlinkStore`（result `queued_pending`）、广播 Topic `omni.command.downlink.broadcast`。见 [PHASE3.md](../PHASE3.md)。

---

## 1. 背景与目标

### 1.1 问题

当前架构偏重**上行**（设备 → 网关 → Kafka → 业务）。生产环境必须支持：

- 平台下发控制指令（OTA、参数配置、远程开锁等）
- 业务微服务通过 MQ 异步触发网关写回设备
- 部分协议要求平台侧主动发包（如 JT808 0x8xxx）

### 1.2 目标

| 目标 | 可度量标准 |
|------|------------|
| 统一下行入口 | 业务仅向标准 Topic 投递 JSON，不感知 TCP/协议细节 |
| 会话精准投递 | 指令只发往**当前在线且已鉴权**的设备连接 |
| 可扩展 | 单节点内存会话；多节点通过设计 02 路由 |
| 可观测 | 下发成功率、超时率、排队深度可监控 |
| 不阻塞 EventLoop | 编解码与写 socket 在约定线程模型内执行 |

### 1.3 非目标（本期不做）

- 设备离线消息持久化与「上线后补发」（可二期，依赖 MQ 延迟队列 + 设备影子）
- 群发 / 广播（需单独 Topic 与消费者组策略）
- WebSocket / HTTP 下行（仅 TCP 网关范围）

---

## 2. 总体架构

```
[ 业务微服务 ] ──publish──► [ Kafka: omni.command.downlink ]
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │  DownlinkConsumer (每网关节点) │
                    │  - 解析 CommandEnvelope      │
                    │  - 路由到本地 Session         │
                    └───────────────┬───────────────┘
                                    │ 命中本地 Session
                                    ▼
                    ┌───────────────────────────────┐
                    │  DownlinkDispatcher            │
                    │  - 限流 / 串行化(按设备)        │
                    │  - 调用 Plugin.encode()        │
                    └───────────────┬───────────────┘
                                    │ writeAndFlush
                                    ▼
                              [ 设备 TCP ]
```

多节点时：Consumer 只处理**本节点持有**的会话；未命中则转发或丢弃（见设计 02）。

---

## 3. 数据模型

### 3.1 CommandEnvelope（Kafka 消息体）

```json
{
  "messageId": "uuid",
  "deviceId": "string",
  "protocol": "JT808",
  "commandType": "REMOTE_CTRL",
  "payload": { },
  "timeoutMs": 5000,
  "traceId": "optional",
  "source": "ota-service",
  "createdAt": 1700000000000
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| messageId | 是 | 幂等键；重试不重复执行（见 §5.3） |
| deviceId | 是 | 与 Session 绑定的全局设备 ID |
| protocol | 是 | 与插件 `pluginId` 一致，防止跨协议误投 |
| commandType | 是 | 插件内枚举或字符串，映射到具体报文 |
| payload | 是 | 协议无关的业务参数，由 Plugin 转码 |
| timeoutMs | 否 | 默认 5000；等待设备 ACK 的上限 |
| traceId | 否 | 链路追踪 |

### 3.2 DownlinkResult（回执 Topic，可选）

Topic：`omni.command.downlink.result`

```json
{
  "messageId": "uuid",
  "deviceId": "string",
  "status": "SUCCESS | TIMEOUT | OFFLINE | REJECTED | ENCODE_ERROR",
  "detail": "string",
  "finishedAt": 1700000000000
}
```

**待确认项 A**：业务是否需要同步回执 Topic，还是仅依赖 Metrics + 日志。

### 3.3 Session 扩展字段

在 `DeviceSession` 中增加：

| 字段 | 类型 | 说明 |
|------|------|------|
| channelId | String | Netty `ChannelId.asShortText()` |
| deviceId | String | 鉴权后绑定 |
| protocolId | String | 嗅探绑定结果 |
| lastActiveAt | long | 心跳刷新 |
| downlinkSerial | AtomicInteger | 协议需要流水号时由插件管理 |

---

## 4. 核心流程

### 4.1 消费与路由

```
DownlinkConsumer.onRecord(record):
  1. 反序列化 CommandEnvelope，校验必填字段
  2. sessionRegistry.get(deviceId) -> Optional<Session>
  3. if empty:
       - 若启用跨节点路由 -> 见设计 02
       - 否则 -> 发布 OFFLINE 到 result Topic，commit offset
  4. if session.protocol != envelope.protocol -> REJECTED
  5. 提交到该 deviceId 专属 Executor（保证同设备指令串行）
  6. downlinkDispatcher.dispatch(session, envelope)
```

### 4.2 下发执行

```
DownlinkDispatcher.dispatch(session, cmd):
  1. 检查 channel.isActive()
  2. plugin = pluginRegistry.get(session.protocolId)
  3. encoded = plugin.encodeDownlink(session, cmd)  // ByteBuf 或 byte[]
  4. 注册 PendingAck(messageId, timeoutMs) 若协议需要 ACK
  5. channel.eventLoop().execute(() -> channel.writeAndFlush(encoded))
  6. 等待 ACK 或超时 -> 发送 DownlinkResult
```

### 4.3 与上行 ACK 的协同

| 模式 | 适用协议 | 行为 |
|------|----------|------|
| FIRE_AND_FORGET | 单向透传 | 写完即 SUCCESS |
| WAIT_DEVICE_ACK | JT808 等 | PendingAck 表匹配上行解析出的应答 |
| WAIT_KAFKA_THEN_DEVICE | 敏感配置 | 先业务落库再下发（业务侧保证，网关不耦合） |

**待确认项 B**：默认模式按协议配置还是全局默认 `WAIT_DEVICE_ACK`。

---

## 5. 接口与模块职责

### 5.1 omni-core 新增

```java
// 下行编解码扩展（ProtocolPlugin 子接口或 default 方法）
Optional<ByteBuf> encodeDownlink(DeviceSession session, CommandEnvelope cmd);

// 上行解码后回调：是否匹配某条待确认下行
boolean matchDownlinkAck(DeviceSession session, Object protocolMessage);
```

### 5.2 omni-network 新增

| 类 | 职责 |
|----|------|
| `SessionRegistry` | `ConcurrentHashMap<deviceId, DeviceSession>`，CONNECT 注册，CLOSED 移除 |
| `DownlinkDispatcher` | 执行下发、PendingAck 超时调度 |
| `DeviceSerialExecutor` | 按 deviceId 分桶单线程，避免同连接并发写 |

### 5.3 omni-bootstrap 新增

| 类 | 职责 |
|----|------|
| `DownlinkConsumer` | Kafka 监听 `omni.command.downlink`，手动 commit |
| `DownlinkResultProducer` | 可选回执 |
| `DownlinkProperties` | Topic、并发度、默认超时 |

### 5.4 幂等

- 内存 `ProcessedMessageCache`（Caffeine，TTL 10min，key=messageId）
- 重复 messageId：直接返回已有结果，不重复写 socket

---

## 6. Kafka 约定

| 项 | 建议值 |
|----|--------|
| Topic | `omni.command.downlink` |
| Key | `deviceId`（保证同设备有序） |
| Partition 数 | ≥ 网关节点数 × 2 |
| Consumer Group | `omni-gateway-downlink-{nodeId}` 或统一 group + 本地过滤 |
| Commit | 处理完成后 commit（失败重试注意幂等） |

**待确认项 C**：每节点独立 Consumer Group（每节点消费全量再过滤）vs 统一 Group + 分区亲和（实现复杂）。**推荐首期**：独立 Group + 本地 Session 过滤，实现简单；流量大时再演进。

---

## 7. 失败与降级

| 场景 | 处理 |
|------|------|
| 设备离线 | OFFLINE + result |
| Channel 不可写（高水位） | 延迟重试 3 次，仍失败则 REJECTED |
| 编码异常 | ENCODE_ERROR，记日志，不 commit 或 DLQ（可配置） |
| 消费堆积 | 告警；可暂停 Consumer（运维开关） |

---

## 8. 配置项

```yaml
omni:
  downlink:
    enabled: true
    topic: omni.command.downlink
    result-topic: omni.command.downlink.result
    result-enabled: true
    consumer-concurrency: 4
    default-timeout-ms: 5000
    pending-ack-max: 10000
    idempotency-ttl-minutes: 10
```

---

## 9. 验收标准（确认后开发）

- [ ] 单节点：在线设备可收到下行，离线返回 OFFLINE
- [ ] 同 deviceId 两条指令顺序执行，无并发写乱序
- [ ] 重复 messageId 不重复发包
- [ ] JT808（或首个插件）完成「下发 → 设备 ACK → result SUCCESS」闭环
- [ ] EventLoop 无阻塞 Kafka 消费逻辑（消费在独立线程池）
- [ ] Metrics：`downlink_total{status}`、`downlink_latency_seconds`

---

## 10. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | 是否需要 result Topic | 需要 |
| B | 默认 ACK 模式 | 按协议配置表 |
| C | 多节点 Consumer 策略 | 首期每节点独立 Group + 本地过滤 |
| D | 离线指令是否二期做 | 是，本期不做 |

**请确认后回复：同意默认 / 或逐项修改意见。**
