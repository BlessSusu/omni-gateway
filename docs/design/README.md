# OmniGateway 详细设计索引

> **实现状态**：第一～三期核心能力已落地，以 [../PHASES.md](../PHASES.md) 与分期专文为准。  
> **分期总览**：[../PHASES.md](../PHASES.md)

## 文档列表

| 编号 | 主题 | 文档 | 实现状态 | 对应分期 |
|------|------|------|----------|----------|
| 01 | 下行通道 | [01-downlink-channel.md](./01-downlink-channel.md) | ✅ 已实现（含 pending/广播，见 Phase 3） | M4 / M7 / M20～M21 |
| 02 | 水平扩展 | [02-horizontal-scaling.md](./02-horizontal-scaling.md) | ✅ Redis 索引 + 分节点 Topic | M7 |
| 03 | 传输安全 | [03-transport-security.md](./03-transport-security.md) | ✅ TLS/mTLS/限流；国密未做 | M8 |
| 04 | 可观测性 | [04-observability.md](./04-observability.md) | ✅ Metrics + JSON 日志 + OTel Span | M5 / M10 |
| 05 | 配置热更新 | [05-config-hot-reload.md](./05-config-hot-reload.md) | ✅ L0～L2 + drain；Nacos 拉取（M22） | M5 / M9 / M22 |
| 06 | SLO 与容量 | [06-slo-metrics.md](./06-slo-metrics.md) | 口径已定；**实测表待 PT-07 填** | M6 / M11 |
| 07 | GB28181 | [07-gb28181.md](./07-gb28181.md) | ✅ 上行 + 下行（M18/M19） | M18 / M19 |

第四期 gRPC / 一致性哈希见 [../PHASE4.md](../PHASE4.md)，暂无独立设计编号文档。

## 依赖关系

```
06-slo-metrics ──依赖──► 04-observability
01-downlink ──依赖──► 02-horizontal-scaling
05-config-hot-reload ──关联──► 02, 03
03-transport-security ──独立（Pipeline 前置）
07-gb28181 ──独立端口 5060，与 JT808 分离
```

## 压测脚本索引

| 编号 | 脚本 | 阶段 |
|------|------|------|
| PT-01 | `tools/loadtest/pt01_hold_connections.py` | Phase 1 |
| PT-02 | `tools/loadtest/pt02_uplink_throughput.py` | Phase 1 |
| PT-03 | `tools/loadtest/pt03_downlink_routing.py` | Phase 2 |
| PT-04 | `tools/loadtest/pt04_backpressure_readme.md` | Phase 1 |
| PT-05 | `tools/loadtest/pt05_tls_handshake.py` | Phase 2 |
| PT-06 | `tools/loadtest/pt06_rolling_restart.py` | Phase 2 |
| PT-07 | `tools/loadtest/pt07_device_capacity.py` | 容量填表（推荐） |

报告模板：[../loadtest/BASELINE-REPORT.md](../loadtest/BASELINE-REPORT.md)

## 历史「待确认」说明

各文档末尾 **待确认清单** 为立项时评审用。开发中已按 [PHASES.md](../PHASES.md) 默认选项实现（如分节点 Topic C1、踢旧连接等）。若需变更行为，以代码 + 分期文档同步修改为准。
