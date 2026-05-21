# 详细设计 06：SLO 与容量指标（SLO & Capacity）

| 属性 | 值 |
|------|-----|
| 状态 | **口径已定；实测容量待 PT-07 填表** |
| 依赖设计 | [04-observability](./04-observability.md), [02-horizontal-scaling](./02-horizontal-scaling.md) |

---

## 1. 背景与目标

### 1.1 问题

- 「单节点十万连接」缺乏可验证定义
- 无 SLO 则无法约定扩容、告警与发布门禁
- 压测与生产指标需要对齐同一套口径

### 1.2 目标

- 定义**服务级别目标（SLO）**、**错误预算**、**容量基线**
- 给出压测方法与验收门槛
- 为运维提供扩容计算公式

---

## 2. 服务定义

### 2.1 服务边界

| 在边界内 | 在边界外 |
|----------|----------|
| TCP 接入、嗅探、鉴权、协议解析 | 业务微服务处理延迟 |
| 上行写入 Kafka（含 ack） | Kafka 集群自身 SLA |
| 下行从消费到写 socket | 设备端网络质量 |

### 2.2 用户画像

- **设备**：IoT / 车载终端，长连接，心跳 30s-300s
- **报文**：平均 200B-2KB，峰值 10KB
- **上行频率**：每设备 0.1-10 msg/s（按业务可调）

---

## 3. SLI 与 SLO 定义

### 3.1 SLI（服务级别指标）

| SLI | 计算方式 |
|-----|----------|
| 可用性 | 成功建立业务态连接数 / 连接尝试（排除主动拒绝黑名单） |
| 上行成功率 | `omni_messages_uplink_total{status=ok}` / 总解码条数 |
| 上行延迟 | Kafka ack 时间 - 收包时间（网关内） |
| 嗅探成功率 | 1 - `sniff_failures / connections_accepted` |
| 下行成功率 | `downlink{status=SUCCESS} / downlink_total` |

### 3.2 SLO（建议值，待确认）

| SLO 项 | 目标 | 窗口 |
|--------|------|------|
| 网关可用性 | **99.9%** | 30 天 |
| 上行发布成功 | **99.5%** | 30 天 |
| 嗅探成功率 | **≥ 99%**（已知合法设备场景） | 7 天 |
| 网关内上行延迟 P99 | **< 200ms**（不含 Kafka 集群故障） | 7 天 |
| 下行端到端 P99 | **< 2s**（含设备 ACK） | 7 天 |

**待确认项 A**：以上百分比是否满足业务合同；车联网可能要求 99.95%。

### 3.3 错误预算

```
错误预算 = 1 - SLO
例：99.9% 月可用 -> 43.2 分钟/月 不可用
```

预算耗尽策略：冻结功能发布、优先稳定性修复。

---

## 4. 容量基线

### 4.1 单节点参考容量（需压测验证）

**假设环境**：8C16G，Java 17，G1GC，千兆网卡，Kafka 同机房。

| 维度 | 基线（保守） | 拉伸目标（调优后） |
|------|--------------|-------------------|
| 并发连接 | **30,000** | 80,000+ |
| 上行吞吐 | **50,000 msg/s** | 100,000 msg/s |
| 平均消息 | 512 B | - |
| 入站带宽 | ~200 Mbps | ~500 Mbps |
| 堆内存 | 8G 堆，连接元数据 ~1-2KB/连接 | - |
| 直接内存 | Netty 512M-1G 上限需显式配置 | - |

**待确认项 B**：对外承诺以「保守基线」还是「拉伸目标」。

### 4.2 连接内存估算

```
每连接 ≈ Channel 对象 + Session + 缓冲区
        ≈ 1KB~4KB（视 ByteBuf 分配策略）
30k 连接 ≈ 30~120 MB 仅会话
+ JVM 堆 overhead、协议对象池
建议：预留 50% 内存余量
```

### 4.3 扩容公式

```
所需节点数 = ceil(总设备连接数 / 单节点目标连接数 * 1.3)
           （1.3 为冗余系数，含缩容 drain）
```

示例：100 万在线设备，单节点 3 万 -> `ceil(1000000/30000*1.3) = 44` 节点。

---

## 5. 性能预算（单请求路径）

| 阶段 | P99 预算 |
|------|----------|
| 嗅探 | < 50ms（首包已到） |
| 解码单帧 | < 5ms |
| 物模型转换 | < 10ms |
| Kafka send + ack | < 100ms（依赖集群） |
| **网关内合计** | **< 200ms** |

超过预算时记录 `slow_path_total` 并采样 trace。

---

## 6. 压测方案

### 6.1 工具与脚本（仓库已提供）

| 工具 | 路径 / 用途 |
|------|-------------|
| 设备模拟器 | `tools/device_simulator.py`、`tools/jt808_device_simulator.py` |
| PT-01 长连接 | `tools/loadtest/pt01_hold_connections.py` |
| PT-02 上行吞吐 | `tools/loadtest/pt02_uplink_throughput.py` |
| PT-03 下行路由 | `tools/loadtest/pt03_downlink_routing.py` |
| PT-04 背压说明 | `tools/loadtest/pt04_backpressure_readme.md` |
| PT-05 TLS | `tools/loadtest/pt05_tls_handshake.py` |
| PT-06 drain | `tools/loadtest/pt06_rolling_restart.py` |
| **PT-07 容量** | `tools/loadtest/pt07_device_capacity.py`（渐进建连 + `omni_connections_active` 对照） |
| 报告模板 | [../loadtest/BASELINE-REPORT.md](../loadtest/BASELINE-REPORT.md) |

### 6.2 场景

| 场景 ID | 描述 | 通过标准 |
|---------|------|----------|
| PT-01 | 长连接，周期性 telemetry | 连接数稳定，无泄漏 |
| PT-02 | 多设备持续上行 | 上行 SLO 达标，Kafka lag 可控 |
| PT-03 | 分节点下行 Topic | 仅目标节点写 socket |
| PT-04 | Kafka 人为延迟 | 背压生效，无 OOM |
| PT-05 | TLS 握手 + 业务 | 握手成功 |
| PT-06 | 滚动重启单节点 | drain 后会话清零 |
| **PT-07** | 目标 N 连接保持 T 秒 | 客户端与网关 `omni_connections_active` 一致，填容量表 |

### 6.3 压测报告模板

- 环境配置、JVM 参数、Netty 参数
- 各场景 SLI 实测 vs SLO
- GC 暂停 P99、CPU、带宽、直接内存
- 瓶颈结论与调优项

---

## 7. JVM 与 Netty 调优清单（参考）

```bash
-Xms8g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Dio.netty.allocator.type=pooled
-Dio.netty.leakDetection.level=simple  # 仅测试
```

```yaml
omni:
  netty:
    boss-threads: 1
    worker-threads: 0   # 0 = 默认 2*CPU
    so-backlog: 4096
    allocator: pooled
```

**待确认项 C**：是否在文档中固定 JVM 参数为交付标准。

---

## 8. 告警与 SLO 联动

| 告警 | 条件 | SLO 关联 |
|------|------|----------|
| 可用性下降 | 5m 内连接失败率 > 1% | 消耗错误预算 |
| 上行失败 | uplink 失败率 > 0.5% | 上行 SLO |
| P99 延迟 | kafka_publish P99 > 200ms 持续 10m | 延迟 SLO |
| 连接数接近上限 | active > 0.85 * 容量基线 | 扩容 |

---

## 9. 发布门禁

新版本上线前：

- [ ] PT-02 或等价回归通过
- [ ] 无 Critical 级别内存泄漏
- [ ] `omni_sniff_failures` 无异常抬升
- [ ] 变更记录与 configVersion 对齐

---

## 10. 验收标准（本设计文档）

- [ ] 团队书面确认 SLO 表（§3.2）
- [ ] 单节点容量以压测报告为准填入 §4.1
- [ ] Grafana 展示 SLI 仪表盘
- [ ] 扩容公式写入运维手册

---

## 11. 待确认清单

| 编号 | 问题 | 建议默认 |
|------|------|----------|
| A | 可用性 SLO | 99.9% / 30 天 |
| B | 对外承诺连接数 | 保守 30k/节点 |
| C | JVM 参数是否标准化 | 是，写入部署模板 |
| D | 压测是否上线前必做 | 是，PT-01/02/04 必做 |

**请确认后纳入开发里程碑与测试计划。**
