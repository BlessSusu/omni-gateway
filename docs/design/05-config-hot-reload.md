# 详细设计 05：配置热更新（Config Hot Reload）

| 属性 | 值 |
|------|-----|
| 状态 | **待确认** |
| 依赖模块 | omni-network, omni-bootstrap |
| 关联设计 | [02-horizontal-scaling](./02-horizontal-scaling.md), [03-transport-security](./03-transport-security.md) |

---

## 1. 背景与目标

### 1.1 问题

- 端口监听、协议插件列表、嗅探阈值、TLS 证书、限流参数等需从 Nacos 动态调整
- 粗暴重启会导致大量设备断连
- 部分配置**不能**热改（如已绑定协议的 Pipeline）

### 1.2 目标

| 目标 | 说明 |
|------|------|
| 分类热更 | 区分「立即生效」「仅新连接」「需 drain」 |
| 安全回滚 | 配置校验失败则保留旧版 |
| 可观测 | 配置版本号、刷新事件可审计 |

---

## 2. 配置分类

### 2.1 热更级别

| 级别 | 类型 | 示例 | 行为 |
|------|------|------|------|
| **L0 即时** | 开关/阈值 | 嗅探超时、限流速率、日志采样 | 原子替换，影响所有连接 |
| **L1 新连接** | 端口-插件映射 | 某端口新增协议插件 | 新 TCP 用新配置；已建立连接不变 |
| **L2 监听变更** | 增删端口 | 新增 9090 监听 | 启动新 Server；删端口需 drain |
| **L3 安全敏感** | TLS 证书 | server.pem 更换 | 新连接新 Context；见设计 03 |
| **L4 禁止热更** | JVM/线程池 | 需重启 | 文档标明 |

### 2.2 配置模型（Nacos dataId 建议）

```yaml
# dataId: omni-gateway.yaml
configVersion: 12
updatedAt: "2026-05-20T10:00:00Z"

listeners:
  - port: 8081
    tls: false
    plugins: [JT808, CUSTOM_A]
    pluginPriority: [JT808, CUSTOM_A]
    sniff:
      maxBytes: 256
      timeoutMs: 5000
      minProbeLength: 2
  - port: 8443
    tls: true
    plugins: [JT808]

security:
  ipDenyList: ["10.0.0.1"]
  connectionRatePerIp: 50

kafka:
  uplinkTopic: omni.device.uplink

downlink:
  enabled: true
```

---

## 3. 架构组件

```
[Nacos] --push/listen--> [ConfigRefresher]
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
      [PortListenerManager] [SniffConfig] [SecurityConfig]
              │               │               │
              ▼               ▼               ▼
         Netty Server    新连接 Sniff    IpFilter 等
```

### 3.1 核心类

| 类 | 职责 |
|----|------|
| `GatewayConfigSnapshot` | 不可变配置快照 |
| `ConfigRefresher` | 监听 Nacos，校验，原子替换 `AtomicReference<Snapshot>` |
| `PortListenerManager` | 管理 `ServerBootstrap` 生命周期 |
| `ConfigValidator` | 端口冲突、插件 ID 存在性、证书可读 |

---

## 4. 热更新流程

### 4.1 刷新主流程

```
onConfigChange(newYaml):
  1. parse -> GatewayConfigSnapshot
  2. ConfigValidator.validate(snapshot) 
       - fail -> 告警，保留 oldSnapshot
  3. diff(old, new) -> List<ChangeAction>
  4. 按 action 类型顺序执行（L2 先于 L0 删端口等）
  5. configVersion 指标 + 审计日志
```

### 4.2 端口新增（L2）

```
if newPort not in activeServers:
  startServerBootstrap(port, snapshot.getListener(port))
  register in activeServers
```

### 4.3 端口删除（L2 + drain）

```
drainPort(port):
  1. mark port draining（拒绝新连接：在 Boss 层关闭 accept 或移除 listener）
  2. 对现有 channel：可选发送协议层下线通知
  3. 等待 connections_active{port} == 0 或 drainTimeout
  4. shutdown ServerBootstrap
  5. 从 activeServers 移除
```

**待确认项 A**：`drainTimeout` 默认 300s 还是强制立即 close。

### 4.4 插件列表变更（L1）

- **已连接**：保持原 `ProtocolPlugin` 绑定（Pipeline 已固定）
- **新连接**：使用新 `pluginPriority` 做嗅探

### 4.5 嗅探/限流参数（L0）

```java
// SniffHandler 每次 read 时从 AtomicReference 读最新阈值
SniffConfig cfg = configRef.get().sniffFor(port);
```

---

## 5. Drain 与运维协作

### 5.1 节点级 drain（缩容）

```yaml
omni:
  ops:
    draining: false   # 可通过 Nacos 或 actuator 设置
```

```
set draining=true:
  - 停止向 LB 注册（K8s preStop / 运维摘流）
  - 拒绝新 TCP（Boss channel close 或 FirewallHandler）
  - 等待既有连接自然结束
  - downlink consumer stop
  - 进程退出
```

### 5.2 Actuator 端点（建议）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/actuator/omni/drain` | POST | 触发本节点 drain |
| `/actuator/omni/config` | GET | 当前 configVersion |
| `/actuator/omni/listeners` | GET | 活跃端口与连接数 |

---

## 6. 校验规则

| 规则 | 失败处理 |
|------|----------|
| 端口 1-65535 不重复 | 拒绝整单配置 |
| plugins 均在 Registry 存在 | 拒绝 |
| tls=true 但证书路径无效 | 拒绝 L3 变更，保留旧 TLS |
| 删除最后一个 listener | 拒绝（防止无意裸奔） |
| sniff.maxBytes < minProbeLength | 拒绝 |

---

## 7. 与 Spring Cloud 集成

```java
@RefreshScope  // 仅用于非 Netty 的 Bean（如 Kafka topic 名）
```

**注意**：Netty 相关**不用** `@RefreshScope` 重建 Channel，统一走 `ConfigRefresher` 显式 diff。

监听方式：

- Nacos `Listener` 回调
- 或 Spring Cloud `@NacosConfigListener`

---

## 8. 失败与回滚

| 场景 | 行为 |
|------|------|
| 新配置校验失败 | 不替换，旧配置继续 |
| 新端口启动失败 | 回滚该端口变更，告警 |
| drain 超时 | 强制 close 剩余连接 + 告警 |
| 配置版本回退 | Nacos 历史版本 -> 再次触发 refresh |

---

## 9. 配置项

```yaml
omni:
  config:
    nacos-data-id: omni-gateway.yaml
    refresh-interval-ms: 0   # 0=仅推送
    drain-timeout-seconds: 300
    allow-delete-last-listener: false
```

---

## 10. 验收标准

- [ ] 修改嗅探超时无需重启，新读包即生效
- [ ] 新增端口 30s 内可接受连接
- [ ] 删除端口：drain 期间无新连接，超时后端口关闭
- [ ] 错误配置不导致服务不可用（保留旧快照）
- [ ] configVersion 在日志与 metric 中可见

---

## 11. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | 删端口 drain 超时 | 300s，超时强制关闭 |
| B | 已连接是否强制切新插件 | 否，仅新连接 |
| C | actuator drain 是否首期做 | 是 |
| D | 配置存储 | Nacos 单 dataId |

**请确认后开发。**
