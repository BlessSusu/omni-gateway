# OmniGateway 压测基线报告

| 项 | 值 |
|----|-----|
| 日期 | _填写_ |
| 版本 | 1.0.0-SNAPSHOT |
| 环境 | _CPU / 内存 / JDK / 节点数_ |
| 执行人 | _填写_ |

**脚本索引**：[design/README.md](../design/README.md#压测脚本索引)。容量压测优先使用 **PT-07**（`tools/loadtest/pt07_device_capacity.py`）。

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
# PT-01 快速验证
python tools/loadtest/pt01_hold_connections.py --connections 1000 --duration-sec 300

# PT-07 容量压测（渐进建连 + 网关指标对照，推荐填表）
python tools/loadtest/pt07_device_capacity.py --connections 5000 --ramp-sec 120 --duration-sec 300 \
  --metrics-url http://127.0.0.1:8080/actuator/prometheus
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

---

# Phase 2 压测基线（生产化）

| 项 | 值 |
|----|-----|
| 日期 | _填写_ |
| Redis | docker compose redis:6379 |
| 下行 Topic | `omni.command.downlink.{nodeId}` |

## PT-03 下行路由

```bash
python tools/loadtest/pt03_downlink_routing.py --device-id <id> --node-id <nodeId>
# 或迁移模式: --use-router
```

| 指标 | 目标 | 实测 |
|------|------|------|
| `omni_downlink_skip_not_local_total` 增速 | ≈ 0 | |
| 仅目标节点写 socket | 是 | |

## PT-05 TLS

```bash
python tools/loadtest/pt05_tls_handshake.py --port 9443 --insecure
```

| 指标 | 目标 | 实测 |
|------|------|------|
| TLS 握手 | 成功 | |
| 嗅探+业务 | 正常 | |

## PT-06 滚动重启

```bash
python tools/loadtest/pt06_rolling_restart.py --timeout-sec 120
```

| 指标 | 目标 | 实测 |
|------|------|------|
| drain 后会话清零 | ≤ timeout | |

## Phase 2 结论

- [x] 代码交付 M7～M11（见 [PHASE2.md](../PHASE2.md)）
- [ ] 压测实测通过（待填表）
