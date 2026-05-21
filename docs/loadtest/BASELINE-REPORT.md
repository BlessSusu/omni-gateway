# OmniGateway 第一期压测基线报告

| 项 | 值 |
|----|-----|
| 日期 | _填写_ |
| 版本 | 1.0.0-SNAPSHOT |
| 环境 | _CPU / 内存 / JDK / 节点数_ |
| 执行人 | _填写_ |

## 1. 压测环境

| 配置 | 值 |
|------|-----|
| JVM | `-Xms4g -Xmx4g -XX:+UseG1GC` |
| 监听端口 | 9000 (simple-frame + jt808), 9001 (jt808) |
| Kafka | _地址 / 分区数_ |
| 网关节点 | 1 |

## 2. PT-01 长连接

**命令**

```bash
python tools/loadtest/pt01_hold_connections.py --connections 1000 --duration-sec 300
```

| 指标 | 目标 | 实测 |
|------|------|------|
| `omni_connections_active` 稳定 | ≈ 1000 | |
| 24h 无 OOM（或 5min 无泄漏趋势） | 通过 | |
| 堆内存 | 平稳 | _MB_ |

## 3. PT-02 上行吞吐

**命令**

```bash
python tools/loadtest/pt02_uplink_throughput.py --devices 50 --duration-sec 60
```

| 指标 | 目标 | 实测 |
|------|------|------|
| 上行 aggregate rate | _记录 msg/s_ | |
| `omni_messages_uplink_total{status="ok"}` 占比 | ≥ 99.5% | |
| `omni_kafka_publish_seconds` P99 | < 200ms | _ms_ |

## 4. PT-04 背压

见 [tools/loadtest/pt04_backpressure_readme.md](../../tools/loadtest/pt04_backpressure_readme.md)

| 指标 | 实测 |
|------|------|
| `omni_kafka_publish_backpressure` 曾置 1 | 是 / 否 |
| 堆使用恢复平稳 | 是 / 否 |

## 5. 容量结论（填入设计 06）

| 维度 | 保守基线（实测） |
|------|------------------|
| 单节点并发连接 | |
| 上行吞吐 (msg/s) | |
| 备注 | |

## 6. 结论

- [ ] 达到第一期 MVP 验收
- [ ] 需调优后复测
