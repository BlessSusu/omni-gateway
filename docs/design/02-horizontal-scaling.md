# 详细设计 02：水平扩展（Horizontal Scaling）

| 属性 | 值 |
|------|-----|
| 状态 | **待确认** |
| 依赖模块 | omni-network, omni-bootstrap |
| 关联设计 | [01-downlink-channel](./01-downlink-channel.md), [05-config-hot-reload](./05-config-hot-reload.md) |

---

## 1. 背景与目标

### 1.1 问题

- TCP 连接与 `DeviceSession` **有状态**，绑定在单个网关节点进程内
- L4 负载均衡默认轮询会导致：同一设备重连到不同节点、下行找不到会话
- 节点扩缩容时连接需平滑迁移

### 1.2 目标

| 目标 | 说明 |
|------|------|
| 无状态接入面 | 任意节点可接受新连接（嗅探、鉴权、上行 Kafka） |
| 有状态会话面 | 会话仅存本机内存；跨机下行可路由或拒绝 |
| 弹性伸缩 | 增加节点分担**新连接**；缩容前 drain |
| 设备重连 | 短断线重连可落到任意节点，业务通过 deviceId 幂等 |

### 1.3 设计原则

**网关不做分布式 Session 集群（首期）**：避免 Redis 同步每条连接状态带来的延迟与一致性难题。采用 **「本地会话 + 路由旁路」** 分层。

---

## 2. 方案选型

### 2.1 对比

| 方案 | 优点 | 缺点 | 推荐阶段 |
|------|------|------|----------|
| **A. 粘性 LB（源 IP / deviceId）** | 实现简单，下行天然本地 | NAT 下源 IP 不稳定；未鉴权前无 deviceId | 辅助手段 |
| **B. 本地会话 + 下行广播过滤** | 实现快，与 01 设计一致 | 每节点消费全量下行，浪费带宽 | **首期推荐** |
| **C. Redis 会话索引** | 下行精准路由到节点 | 需维护 nodeId、心跳、故障清理 | 二期 |
| **D. 一致性哈希环** | 扩缩容只影响相邻节点 | 需网关间 RPC 或消息总线转发 | 大规模 |

### 2.2 推荐路径

```
首期 (MVP)     : 方案 B — 下行每节点消费 + 本地 Session 命中才发送
二期 (规模)    : 方案 C — Redis: deviceId -> nodeId + 下行只投递目标节点
可选 (超大流)  : 方案 D — 网关集群内 gRPC 转发下行
```

**待确认项 A**：首期是否接受方案 B 的 Kafka 冗余消费成本。

---

## 3. 首期架构（方案 B）

```
                    [ L4 LB - 轮询 ]
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    [ Gateway-1 ]     [ Gateway-2 ]     [ Gateway-3 ]
    Session 本地      Session 本地      Session 本地
         │                 │                 │
         └─────────────────┴─────────────────┘
                           │
              各节点 DownlinkConsumer 订阅同一 Topic
              仅 deviceId 在本地 Registry 时真正 write
```

### 3.1 节点标识

每个进程启动时生成：

```text
nodeId = ${spring.application.name}-${POD_IP或host}-${server.port}
```

写入：

- 上行 Thing Model 附加 `gatewayNodeId`（便于排查）
- 离线事件带 `gatewayNodeId`

### 3.2 SessionRegistry（本地）

```java
interface SessionRegistry {
  void bind(String deviceId, DeviceSession session);
  void unbind(String deviceId);
  Optional<DeviceSession> get(String deviceId);
  int localSessionCount();
  Set<String> localDeviceIds(); // 仅运维/debug
}
```

- **仅本机内存**，不 replication
- 鉴权成功后 `bind(deviceId)`；连接关闭 `unbind`

### 3.3 下行行为（配合设计 01）

```
收到 CommandEnvelope:
  if sessionRegistry.get(deviceId).isPresent()
    -> dispatch
  else
    -> 记 metric downlink_skip_not_local，commit offset
```

### 3.4 上行

无状态：任意节点解析后送 Kafka，**不依赖**会话在哪台机器。

---

## 4. 二期架构（方案 C - Redis 索引）

### 4.1 注册表结构

```
Key:   omni:session:{deviceId}
Value: { "nodeId": "...", "protocol": "JT808", "connectedAt": 1700000000 }
TTL:   心跳间隔 × 3（由网关续期）
```

### 4.2 连接生命周期

```
鉴权成功:
  Redis SET omni:session:{deviceId} nodeId EX ttl
  本地 SessionRegistry.bind

心跳 / 业务包:
  续期 Redis TTL

连接关闭:
  本地 unbind
  Redis DEL（仅当 value.nodeId == 本机 nodeId，防止误删新连接）
```

### 4.3 精准下行

**方式 C1 - 分节点 Topic（推荐）**

```
omni.command.downlink.{nodeId}
```

业务或「下行路由服务」先查 Redis 得 nodeId，再发到对应 Topic；每网关只订阅自己的 Topic。

**方式 C2 - 统一 Topic + 头信息**

消息带 `targetNodeId`，各节点 Consumer 过滤。

**待确认项 B**：二期选用 C1 还是 C2。

---

## 5. 负载均衡与连接

### 5.1 L4 配置建议

| 项 | 建议 |
|----|------|
| 算法 | 轮询或最少连接（least conn） |
| 超时 | 大于设备心跳间隔 |
| 健康检查 | TCP check 网关监听端口 |

### 5.2 粘性（可选增强）

若设备在鉴权前使用固定源 IP 且稳定，可配置 `hash $remote_addr` 提高「重连落同一节点」概率，**不能作为唯一依赖**。

### 5.3 设备重连

设备断线后重连到任意节点：

- 新 Session 覆盖 Redis（二期）
- 业务侧以 `deviceId` 幂等，容忍短暂双连接窗口（旧连接 FIN 延迟）

**双连接窗口策略（待确认项 C）**：

| 策略 | 行为 |
|------|------|
| 踢掉旧连接 | 新鉴权成功时 close 旧 Channel |
| 拒绝新连接 | 旧连接存活则拒绝新鉴权 |
| 并存 | 不推荐 |

**建议默认**：踢掉旧连接（车联网常见）。

---

## 6. 扩缩容流程

### 6.1 扩容

1. 启动新网关实例，注册到 LB
2. 新连接自动分布到新节点
3. 已有连接不受影响直至自然重连

### 6.2 缩容（配合设计 05）

1. 标记节点 `draining=true`（停止 LB 新连接）
2. 等待现有连接关闭或超时
3. 下行 Consumer 停止
4. 进程退出

---

## 7. 数据面 vs 控制面

| 面 | 状态 | 存储 |
|----|------|------|
| 数据面（TCP 字节流） | 有状态 | 本机 Channel |
| 会话索引（二期） | 有状态 | Redis |
| 配置（端口、插件） | 无状态 | Nacos |
| 上行事件 | 无状态 | Kafka |

---

## 8. 配置项

```yaml
omni:
  cluster:
    node-id: ${HOSTNAME:localhost}-${server.port}
    downlink-mode: LOCAL_FILTER   # LOCAL_FILTER | REDIS_ROUTE (二期)
    redis:
      enabled: false
      key-prefix: omni:session:
      ttl-seconds: 180
    session:
      kick-old-on-reauth: true
```

---

## 9. 验收标准

**首期**

- [ ] 3 节点 + LB 轮询：设备连接任意节点可上行 Kafka
- [ ] 下行仅持有会话的节点真正写 socket，其他节点 skip
- [ ] 缩容 drain 后无强制断连外的异常丢包

**二期（若启用 Redis）**

- [ ] 下行只被一个节点消费且送达
- [ ] 节点宕机后 TTL 过期，下行返回 OFFLINE

---

## 10. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | 首期下行冗余消费 | 接受 |
| B | 二期路由 Topic 方案 | C1 分节点 Topic |
| C | 重复登录策略 | 踢旧连接 |
| D | 二期 Redis 是否纳入首期 | 否 |

**请确认后进入开发排期。**
