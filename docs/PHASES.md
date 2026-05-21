# OmniGateway 分期实施文档

| 属性 | 值 |
|------|-----|
| 版本 | v1.0 |
| 状态 | **第三期已交付（M18～M22）** |
| 关联 | [总体方案](../OmniGateway.md) · [详细设计索引](./design/README.md) |

---

## 总览

```
第一期 (MVP)          第二期 (生产增强)
─────────────────     ─────────────────────────
可接入 · 可上报        精准下行 · 强安全 · 可运维
可下发 · 可观测        可热更 · 可压测验收
单协议闭环             多节点生产级
```

| 维度 | 第一期 | 第二期 |
|------|--------|--------|
| **目标** | 跑通「设备 ↔ 网关 ↔ Kafka ↔ 业务」最小闭环 | 多节点生产部署、安全合规、运维无重启 |
| **节点规模** | 1～3 节点 + L4 轮询 | 10+ 节点，按容量公式扩容 |
| **下行** | 每节点消费 MQ，本地 Session 命中才发 | Redis 路由，按节点 Topic 精准投递 |
| **安全** | 嗅探防卫、IP 限流、协议鉴权；TLS 可选 | 网关 TLS 默认开启、mTLS、证书热轮换 |
| **配置** | 文件/Nacos 启动加载；L0/L1 热更 | 端口增删、drain 缩容、TLS 热更 |
| **观测** | Prometheus 核心指标 + JSON 日志 | Tracing、告警模板、压测报告填 SLO |

第三期（业务协议与下行增强）、第四期（规模化 gRPC）见 [PHASE3.md](./PHASE3.md)、[PHASE4.md](./PHASE4.md)。

---

## 第一期：MVP（最小可运行产品）

### 1.1 阶段目标

在 **4～8 周**（可按团队人力调整）内交付一套可演示、可联调、可小规模试运行的网关：

1. 设备 TCP 接入，帧头嗅探绑定协议插件  
2. 鉴权通过后上行转 Thing Model JSON 写入 Kafka  
3. 业务经 Kafka 下发指令，在线设备可收到并 ACK（至少 1 个协议，建议 JT808 或首个自定义协议）  
4. 具备基础监控与压测能力，验证 SLO 口径  

### 1.2 交付清单

#### A. 工程骨架（Maven 四模块）

| 模块 | 第一期交付 |
|------|------------|
| **omni-core** | `ProtocolPlugin` 接口；`ThingModel`；`CommandEnvelope` / `DownlinkResult`；`DeviceSession`；`AuthResult` |
| **omni-network** | Netty Server；`SniffHandler`；动态 Pipeline；`SessionRegistry`；心跳/空闲检测；背压 `autoRead`；嗅探防卫 |
| **omni-protocols** | **至少 1 个**完整插件（探测、粘包、鉴权、上下行编解码、物模型转换） |
| **omni-bootstrap** | Spring Boot 启动；插件 Spring 注册；Kafka 上行 Producer；下行 Consumer + Dispatcher |

#### B. 核心能力

| 能力 | 说明 | 设计参考 |
|------|------|----------|
| 多端口监听 | 配置文件定义端口与插件列表；启动时绑定 | [05](./design/05-config-hot-reload.md) L1 |
| 协议嗅探 | 累积缓冲、`minProbeLength`、优先级、`Max_Sniff_Bytes` / `Sniff_Timeout` | [OmniGateway §6](../OmniGateway.md) |
| 会话管理 | 本地 `ConcurrentHashMap<deviceId, Session>`；重连踢旧连接 | [02](./design/02-horizontal-scaling.md) 首期 |
| 上行链路 | Decoder → Thing Model → Kafka（含发送回调与背压） | 主方案 §6 |
| 下行链路 | 消费 `omni.command.downlink`；本地无 Session 则 OFFLINE；按设备串行下发；`result` Topic | [01](./design/01-downlink-channel.md) |
| 水平扩展（首期） | 多节点 + LB 轮询；每节点独立下行 Consumer，**仅本地命中发送** | [02](./design/02-horizontal-scaling.md) |
| 可观测性 | `omni_*` 核心 Metrics；结构化 JSON 日志；`/actuator/prometheus` | [04](./design/04-observability.md) |
| 配置 | `application.yml` + 可选 Nacos；**L0/L1 热更**（嗅探阈值、限流、插件列表对新连接生效） | [05](./design/05-config-hot-reload.md) |
| SLO | 文档定义 SLI/SLO；完成 **PT-01 / PT-02 / PT-04** 压测并填实测容量 | [06](./design/06-slo-metrics.md) |

#### C. 第一期明确不做

| 不做项 | 挪到 |
|--------|------|
| Redis 会话索引、分节点下行 Topic | 第二期 |
| mTLS、证书双证热轮换 | 第二期 |
| 端口删除 drain、Actuator 运维端点 | 第二期 |
| OpenTelemetry 全链路 Trace | 第二期 |
| 设备离线指令补发、下行广播 | 第三期 M20/M21 |
| GB28181 等未排期协议 | **第三期 M18 优先**（见 [PHASE3.md](./PHASE3.md)） |

#### D. 第一期里程碑（建议顺序）

```
M1 工程与 omni-core 接口定义
    └─► M2 omni-network：监听、嗅探、Pipeline、Session、防卫
            └─► M3 首个协议插件 + 上行 Kafka
                    └─► M4 下行 Consumer + 单设备串行 + result
                            └─► M5 Metrics/日志 + 配置 L0/L1 热更 ✅（见 PHASE1-ITERATION.md）
                                    └─► M6 压测 PT-01/02/04 + MVP 验收（脚本+报告模板就绪，待填实测）
```

### 1.3 第一期验收标准

- [ ] 单节点：设备连接 → 嗅探 → 鉴权 → 上行 Kafka 消息字段符合 Thing Model  
- [ ] 单节点：Kafka 下发 → 在线设备收包 →（需 ACK 协议）`result` 为 SUCCESS  
- [ ] 设备离线：下行返回 OFFLINE，不阻塞 Consumer  
- [ ] 3 节点 + LB：上行任意节点可达 Kafka；下行仅持有会话节点写 socket  
- [ ] 嗅探失败 / 超时连接被关闭，无连接泄漏（24h 空连压测）  
- [x] Kafka 失败/堆积时背压生效（`autoRead` + `omni_kafka_publish_backpressure`）  
- [ ] Prometheus 可抓取 `omni_connections_active`、`omni_sniff_failures_total`、`omni_kafka_publish_seconds` 等  
- [ ] 压测报告：连接数、上行 P99、资源占用写入 [06](./design/06-slo-metrics.md) 基线表  

### 1.4 第一期技术参数（默认）

| 项 | 默认值 |
|----|--------|
| Java | 17 |
| 单节点目标连接 | 30,000（保守，压测后修正） |
| 嗅探超时 | 5s |
| 嗅探最大字节 | 256 |
| 下行默认超时 | 5s |
| 下行 Consumer | 每节点独立 Group，本地 Session 过滤 |

---

## 第二期：生产增强

### 2.1 阶段目标

在第一期 MVP 稳定的前提下，支撑 **多节点生产环境** 与 **安全/运维** 要求：

1. 下行不再全集群冗余消费，按设备精准路由到持有会话的节点  
2. 传输加密与设备身份增强（TLS / mTLS）  
3. 配置与证书可热更新，缩容可 drain  
4. 可观测性达到告警与排障闭环；SLO 有正式发布门禁  

建议周期：**4～6 周**（依赖第一期代码质量）。

### 2.2 交付清单

#### A. 水平扩展与下行（设计 01 + 02 二期）

| 能力 | 说明 |
|------|------|
| Redis 会话索引 | `deviceId → nodeId`，鉴权绑定、心跳续期、关闭时 CAS 删除 |
| 精准下行 | `omni.command.downlink.{nodeId}` 或统一 Topic + `targetNodeId`（**确认选 C1 分节点 Topic**） |
| 下行路由服务 | 业务发统一 Topic → 路由组件查 Redis 转发（或业务直发节点 Topic） |
| 移除冗余消费 | `downlink_skip_not_local` 趋近 0（仅故障切换时偶发） |

#### B. 传输安全（设计 03）

| 能力 | 说明 |
|------|------|
| 网关终结 TLS | `SslHandler` 在 Sniff 之前；TLS 1.2+ |
| mTLS（可选开启） | 客户端证书 + 与协议 deviceId 交叉校验 |
| 证书热轮换 | current/next 双证；新连接生效 |
| 接入防护强化 | IP 黑白名单、连接速率限制与第一期对齐并可热更 |

#### C. 配置热更新（设计 05 完整）

| 级别 | 第二期补齐 |
|------|------------|
| L2 | 动态新增监听端口 |
| L2 + drain | 删除端口：停止 accept → 等待连接清零或 300s 超时 → 关闭 Server |
| L3 | TLS 证书热加载 |
| 运维 | `/actuator/omni/drain`、`/config`、`/listeners` |
| 节点 drain | 与 K8s preStop / LB 摘流联动 |

#### D. 可观测性与 SLO（设计 04 + 06）

| 能力 | 说明 |
|------|------|
| OpenTelemetry | 嗅探、解码、Kafka、下行 Span；`traceId` 贯穿上下行 |
| Grafana 模板 | 总览 / 协议 / 嗅探 / Kafka / 下行 / 资源 |
| Prometheus 告警规则 | 连接骤降、嗅探失败率、Kafka P99、背压 |
| 压测全集 | PT-01～PT-06；SLO 达标报告；发布门禁 checklist |
| JVM/Netty 标准化 | 部署模板固定启动参数 |

#### E. 协议与生态（按需）

| 项 | 说明 |
|----|------|
| 第二协议插件 | 如 Modbus / 第二自定义协议，验证多插件嗅探优先级 |
| 配置中心生产化 | Nacos 为主；`configVersion` 审计 |

### 2.3 第二期明确不做

| 不做项 | 说明 |
|--------|------|
| 离线下行补发 | 第三期 M20 |
| 网关间 gRPC 下行转发 | **第四期**（见 [PHASE4.md](./PHASE4.md)） |
| 国密 SM2/SM4 | 第四期或合规单独立项 |

### 2.4 第二期里程碑

```
M7 Redis SessionRegistry + 节点 Topic 下行
    └─► M8 TLS + 证书热更 + mTLS（可选）
            └─► M9 端口 drain + Actuator 运维
                    └─► M10 OTel + Grafana/告警
                            └─► M11 全量压测 PT-01～06 + SLO 验收 + 生产发布
```

### 2.5 第二期验收标准

- [x] 下行（实现）：分节点 Topic + 可选 Router；`downlink_skip_not_local` 主路径移除 — _待压测填表_  
- [x] 节点宕机（实现）：Redis CAS 删除 + TTL；`RedisSessionIndexTest` — _待 E2E_  
- [x] TLS（实现）：`SslContextFactory` + 按端口 `SslHandler`；证书热更 reload — _待 PT-05_  
- [x] drain（实现）：`drainPort` / `omnidrain` / `omnilisteners` — _待 PT-06_  
- [x] Tracing（实现）：`protocol.sniff` → `kafka.uplink.publish` spans + `traceId` — _待 OTel 后端_  
- [ ] 压测：30 天 SLO 口径下 PT-02/05/06 通过，容量表更新 |

---

## 第三期：业务协议与下行增强

详见 [PHASE3.md](./PHASE3.md)。**优先 M18 GB28181**（SIP + MANSCDP+xml），其次离线下发、广播、控制面；**gRPC 不在第三期**。

| 里程碑 | 内容 |
|--------|------|
| M18 | GB28181 解析与上行（当前） |
| M19 | GB28181 下行（推流 URL 等） |
| M20 | 离线下行补发 |
| M21 | 下行广播 |
| M22 | 控制面 API + Nacos |

## 第四期：规模化与 gRPC

详见 [PHASE4.md](./PHASE4.md)。网关间 **gRPC 下行转发**、一致性哈希、国密（按需）。

---

## 两期对照表

| 能力域 | 第一期 | 第二期 |
|--------|--------|--------|
| TCP 接入 + 嗅探 | ✅ | ✅ |
| 协议插件 | 1 个完整 | 2+ 个 |
| 上行 Kafka | ✅ | ✅ |
| 下行 Kafka | ✅ 本地过滤 | ✅ Redis 精准路由 |
| Session | 仅本地内存 | 本地 + Redis 索引 |
| TLS | 可选/明文可接受 | 默认开启 |
| mTLS | ❌ | ✅ 可配置 |
| 配置热更 | L0/L1 | L0～L3 + drain |
| Metrics | 核心集 | 核心集 + 告警 |
| Tracing | ❌ | ✅ |
| 压测 | PT-01/02/04 | PT-01～06 |
| 单节点连接承诺 | 压测填表（目标 3 万） | 生产 SLO 门禁 |

---

## 资源与依赖建议

| 角色 | 第一期 | 第二期 |
|------|--------|--------|
| 后端开发 | 2 人 × 核心 + 插件 | 1～2 人 × Redis/下行/安全 |
| 运维 | Kafka、Nacos、Prometheus 环境 | LB、Redis、证书、Grafana |
| 测试 | 协议模拟器、压测脚本 | 全量压测、混沌（单节点 kill） |

**外部依赖**

- 第一期：Kafka、（可选）Nacos  
- 第二期：+ Redis、证书管理、（可选）OTel Collector  

---

## 确认后行动

1. 评审本分期文档 + [详细设计](./design/README.md) 待确认项  
2. 回复「第一期按本文开工」或修改项  
3. 第一期 M1 起：初始化 Maven 工程与 `omni-core` 接口  

---

*与 [OmniGateway.md](../OmniGateway.md) §8 同步；冲突时以本文分期为准。*
