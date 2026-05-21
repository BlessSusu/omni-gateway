# OmniGateway 详细设计索引

> **状态：第一期已确认并开工** — 实现以 [../PHASES.md](../PHASES.md) 为准。  
> **分期总览**：[../PHASES.md](../PHASES.md)（第一期 / 第二期做什么）

## 文档列表

| 编号 | 主题 | 文档 | 状态 | 建议开发阶段 |
|------|------|------|------|--------------|
| 01 | 下行通道 | [01-downlink-channel.md](./01-downlink-channel.md) | 待确认 | Phase 1（MVP 必做） |
| 02 | 水平扩展 | [02-horizontal-scaling.md](./02-horizontal-scaling.md) | 待确认 | Phase 1 首期 / Phase 2 Redis |
| 03 | 传输安全 | [03-transport-security.md](./03-transport-security.md) | 待确认 | Phase 1 TLS / Phase 2 mTLS |
| 04 | 可观测性 | [04-observability.md](./04-observability.md) | 待确认 | Phase 1（与核心同步） |
| 05 | 配置热更新 | [05-config-hot-reload.md](./05-config-hot-reload.md) | 待确认 | Phase 1 基础 / Phase 2 drain |
| 06 | SLO 与容量 | [06-slo-metrics.md](./06-slo-metrics.md) | 待确认 | Phase 1 定义 / 压测后填数 |
| 07 | GB28181 协议 | [07-gb28181.md](./07-gb28181.md) | 开发中 | **Phase 3 M18** |

## 依赖关系

```
06-slo-metrics ──依赖──► 04-observability
01-downlink ──依赖──► 02-horizontal-scaling
05-config-hot-reload ──关联──► 02, 03
03-transport-security ──独立（Pipeline 前置）
```

## 建议确认顺序

1. **06 SLO** — 先定指标与容量承诺，避免实现偏离
2. **04 可观测性** — 与核心开发同步埋点
3. **02 水平扩展** — 决定首期下行路由策略（影响 01）
4. **01 下行通道** — 核心业务闭环
5. **05 配置热更新** — 运维与发布
6. **03 传输安全** — 按合规优先级排期

## 确认方式

对每份文档末尾 **「待确认清单」** 逐项回复，例如：

```text
01: 同意默认 / A=需要result, C=独立ConsumerGroup
02: 同意默认
...
```

或统一回复：**「全部同意建议默认」** 后进入开发。
