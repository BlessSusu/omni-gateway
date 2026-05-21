# 详细设计 04：可观测性（Observability）

| 属性 | 值 |
|------|-----|
| 状态 | **已实现**（Phase 2：Micrometer OTel bridge、`protocol.sniff` / `kafka.uplink.publish` / `downlink.dispatch` spans，见 `docs/observability/`） |
| 依赖模块 | omni-core, omni-network, omni-bootstrap |
| 技术栈 | Micrometer, Prometheus, Grafana；可选 OpenTelemetry |

---

## 1. 背景与目标

### 1.1 问题

- 多协议、多端口、多节点下故障定位困难
- 需区分：网络问题、嗅探失败、解析错误、Kafka 慢、下行失败
- 企业运维依赖 Prometheus 告警与大盘

### 1.2 目标

| 支柱 | 目标 |
|------|------|
| Metrics | 黄金指标 + 协议/端口维度；支持告警规则 |
| Logging | 结构化日志，可关联 traceId / deviceId |
| Tracing | 关键路径 Span：嗅探、解析、上行 Kafka、下行写 |

### 1.3 非目标

- 全量 Payload 日志（默认关闭，采样可开）
- 自建 APM 平台（对接现有即可）

---

## 2. 指标设计（Metrics）

### 2.1 命名规范

- 前缀：`omni_`
- 标签（低基数）：`protocol`, `port`, `node_id`, `status`, `reason`
- **禁止**高基数标签：`deviceId`, `channelId`, `messageId`

### 2.2 核心指标表

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `omni_connections_active` | Gauge | port, protocol | 当前在线连接 |
| `omni_connections_total` | Counter | port, status=accepted/closed | 累计连接 |
| `omni_sniff_duration_seconds` | Histogram | port, protocol, result=ok/fail/timeout | 嗅探耗时 |
| `omni_sniff_failures_total` | Counter | port, reason | 超时、超字节、无匹配 |
| `omni_auth_failures_total` | Counter | protocol, reason | 鉴权失败 |
| `omni_messages_uplink_total` | Counter | protocol, status | 上行条数 |
| `omni_message_parse_errors_total` | Counter | protocol | 解码失败 |
| `omni_kafka_publish_seconds` | Histogram | topic, status | 上行发 Kafka |
| `omni_kafka_publish_backpressure` | Gauge | - | autoRead=false 次数或状态 |
| `omni_downlink_total` | Counter | protocol, status | 下行结果 |
| `omni_downlink_latency_seconds` | Histogram | protocol | 下发到 ACK |
| `omni_downlink_skip_not_local_total` | Counter | - | 多节点未命中 |
| `omni_bytes_received_total` | Counter | port | 入站字节 |
| `omni_bytes_sent_total` | Counter | port | 出站字节 |
| `omni_eventloop_blocked_seconds` | Timer | - | 可选：EventLoop 阻塞检测 |

### 2.3 JVM / 系统（Spring Boot 默认）

- `jvm_memory_used_bytes`, `process_cpu_usage`, `system_load_average_1m`
- Netty 直接内存：通过自定义 Gauge 暴露 `omni_netty_direct_memory_bytes`

### 2.4 埋点位置

```
ChannelActive        -> connections_total++, gauge++
SniffHandler 结束    -> sniff_duration, sniff_failures
authenticate 结束      -> auth_failures
Decoder 成功/失败      -> messages_uplink / parse_errors
Kafka send 回调        -> kafka_publish_seconds
DownlinkDispatcher     -> downlink_*
setAutoRead(false)     -> backpressure gauge
```

---

## 3. 日志设计（Logging）

### 3.1 格式

JSON 结构化（Logback `logstash-encoder`）：

```json
{
  "ts": "2026-05-20T10:00:00.000Z",
  "level": "INFO",
  "logger": "omni.uplink",
  "nodeId": "gw-1",
  "traceId": "abc",
  "deviceId": "D001",
  "protocol": "JT808",
  "port": 8081,
  "event": "UPLINK_PUBLISHED",
  "messageId": "uuid",
  "durationMs": 12
}
```

### 3.2 日志级别策略

| 事件 | 级别 |
|------|------|
| 连接建立/关闭 | DEBUG（生产可关） |
| 嗅探失败 | WARN（含 remoteIp，不含完整 payload） |
| 鉴权失败 | WARN |
| 解析失败 | ERROR（采样前 32 字节 hex） |
| Kafka 连续失败 | ERROR |

### 3.3 采样

```yaml
omni:
  logging:
    payload-sample-rate: 0.001
    sniff-failure-log-rate: 1.0
```

---

## 4. 分布式追踪（Tracing）

### 4.1 可选实现

- **OpenTelemetry Java Agent** + OTLP 导出（推荐，侵入小）
- 或 Micrometer Tracing + Brave

### 4.2 Span 划分

| Span 名 | 起止 |
|---------|------|
| `tcp.connection` | active -> inactive |
| `protocol.sniff` | 首包 -> 绑定 |
| `protocol.auth` | 鉴权开始 -> 结束 |
| `message.decode` | 单帧解码 |
| `kafka.publish` | 发送 -> ack |
| `downlink.dispatch` | 消费 -> 写 socket / ACK |

### 4.3 传播

- 上行 Thing Model 注入 `traceId`（从 OTel Context 或 HTTP 头继承若存在）
- 下行 CommandEnvelope 携带 `traceId`，延续链路

**待确认项 A**：是否首期接入 OTel，还是仅 Metrics + 日志。

---

## 5. 告警规则（Prometheus 示例）

```yaml
# 连接数异常下降
- alert: OmniConnectionsDrop
  expr: sum(omni_connections_active) < 100
  for: 5m

# 嗅探失败率
- alert: OmniSniffFailureHigh
  expr: rate(omni_sniff_failures_total[5m]) / rate(omni_connections_total[5m]) > 0.1
  for: 10m

# Kafka 发布慢
- alert: OmniKafkaPublishSlow
  expr: histogram_quantile(0.99, rate(omni_kafka_publish_seconds_bucket[5m])) > 2
  for: 5m

# 背压激活
- alert: OmniBackpressureOn
  expr: omni_kafka_publish_backpressure == 1
  for: 3m
```

---

## 6. Grafana 大盘建议

| 面板 | 内容 |
|------|------|
| 总览 | 连接数、QPS、节点分布 |
| 协议 | 按 protocol 的上行/解析错误 |
| 嗅探 | 失败率、P99 嗅探时长 |
| Kafka | 发布延迟、失败 counter |
| 下行 | 成功率、OFFLINE 比例、skip_not_local |
| 资源 | CPU、堆、直接内存 |

---

## 7. 模块职责

| 模块 | 组件 |
|------|------|
| omni-core | `OmniMetrics` 接口常量定义 |
| omni-network | Handler 内埋点；`MetricsChannelHandler` |
| omni-bootstrap | `MeterRegistry` Bean、`/actuator/prometheus` |

### 7.1 统一工具类

```java
public final class OmniMetrics {
  public static void recordSniff(MeterRegistry r, int port, String protocol, 
      String result, long durationMs) { ... }
}
```

避免各 Handler 散落字符串。

---

## 8. 配置项

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  metrics:
    tags:
      application: omni-gateway

omni:
  observability:
    metrics-enabled: true
    tracing-enabled: false
    otlp-endpoint: http://collector:4317
```

---

## 9. 验收标准

- [ ] `/actuator/prometheus` 可抓取全部 `omni_*` 核心指标
- [ ] 标签无 deviceId 等高基数
- [ ] 嗅探/鉴权/解析失败可在 Grafana 按 port、protocol 筛选
- [ ] traceId 贯穿上行 Kafka 消息（tracing 开启时）
- [ ] 告警规则在测试环境可触发验证

---

## 10. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | 首期是否启用 Tracing | 否，仅 Metrics + JSON 日志 |
| B | 日志是否必须 JSON | 是 |
| C | Payload 采样率 | 0.1% |
| D | 大盘由谁维护 | 运维导入 JSON 模板（交付物可提供） |

**请确认后开发。**
