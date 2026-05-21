# OmniGateway

多协议 TCP 网关（Java 17 + Netty + Spring Boot + Kafka）。详见 [OmniGateway.md](OmniGateway.md)、[docs/PHASES.md](docs/PHASES.md)。

**本地安装、Kafka、跑通与测试** → **[docs/SETUP.md](docs/SETUP.md)**（推荐先看）

## 当前能力（第一～三期）

| 分期 | 要点 |
|------|------|
| Phase 1 | simple-frame / JT808、嗅探、上下行 Kafka、背压、配置热更 |
| Phase 2 | Redis 会话索引、分节点下行 Topic、TLS、drain、OTel、Actuator 运维 |
| Phase 3 | GB28181（5060）、离线下发 pending、下行广播、REST 路由 API、Nacos 拉取 |

第四期（gRPC 集群转发）见 [docs/PHASE4.md](docs/PHASE4.md)。

## 快速开始

```powershell
docker compose up -d
.\scripts\create-topics.ps1
.\scripts\create-downlink-topics.ps1 -NodeIds "local-8080"
mvn clean package -DskipTests
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar
python tools\device_simulator.py
```

完整步骤见 **[docs/SETUP.md](docs/SETUP.md)**。

## 端口

| 端口 | 用途 |
|------|------|
| **9000** | simple-frame + JT808（嗅探） |
| **9001** | JT808 |
| **5060** | GB28181（SIP/TCP） |
| **8080** | Actuator + `/api/v1/devices/...` |

## Kafka Topic

| Topic | 方向 |
|-------|------|
| `omni.device.uplink` | 网关 → 业务 |
| `omni.device.lifecycle` | 网关 → 业务（online/offline） |
| `omni.command.downlink` | 业务 → 网关（迁移期统一入口） |
| `omni.command.downlink.{nodeId}` | 业务 → 网关（**推荐**，精准路由） |
| `omni.command.downlink.broadcast` | 业务 → 网关（广播，第三期） |
| `omni.command.downlink.result` | 网关 → 业务 |

## simple-frame 速查

帧格式：`OMNI`(4) + `bodyLen`(2) + JSON + XOR checksum(1)

```json
{"type":"auth","deviceId":"device-001"}
{"type":"telemetry","payload":{"temp":25}}
{"type":"ack","messageId":"cmd-001"}
```

下行示例：

```json
{
  "messageId": "cmd-001",
  "deviceId": "device-001",
  "protocol": "simple-frame",
  "commandType": "setParam",
  "payload": {"key": "interval", "value": 60},
  "timeoutMs": 5000
}
```

## 压测脚本

| 脚本 | 说明 |
|------|------|
| `tools/loadtest/pt01_hold_connections.py` | 长连接 |
| `tools/loadtest/pt07_device_capacity.py` | 容量压测（推荐填基线） |
| `docs/loadtest/BASELINE-REPORT.md` | 实测记录模板 |

## 联调工具

- `tools/device_simulator.py` — simple-frame（9000）
- `tools/jt808_device_simulator.py` — JT808（9001）
- [examples/omni-java-skeleton](examples/omni-java-skeleton/README.md) — Java 设备/业务骨架

## 模块

| 模块 | 说明 |
|------|------|
| omni-core | 接口、物模型、下行/会话抽象 |
| omni-network | Netty、嗅探、会话、下行调度 |
| omni-protocols | simple-frame、JT808、GB28181 |
| omni-bootstrap | Spring Boot、Kafka、Redis、API、Actuator |

## 文档索引

- [docs/PHASES.md](docs/PHASES.md) — 分期总览
- [docs/PHASE3.md](docs/PHASE3.md) — 第三期专文
- [docs/design/README.md](docs/design/README.md) — 详细设计 + 压测表
