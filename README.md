# OmniGateway

多协议 TCP 网关（第一期 MVP）。详见 [OmniGateway.md](OmniGateway.md)、[docs/PHASES.md](docs/PHASES.md)。

**本地安装、Kafka、跑通与测试** → **[docs/SETUP.md](docs/SETUP.md)**（推荐先看）

## 第一期迭代能力

- Kafka 背压（`omni_kafka_publish_backpressure`）
- 配置热更：`POST /actuator/omniconfig`、可选外部 YAML（见 [docs/PHASE1-ITERATION.md](docs/PHASE1-ITERATION.md)）
- 设备上下线 Topic：`omni.device.lifecycle`
- 联调：`tools/device_simulator.py`、`tools/jt808_device_simulator.py`（端口 9001）
- JSON 日志（默认）；压测见 `docs/loadtest/BASELINE-REPORT.md`

## 快速开始

```powershell
docker compose up -d
.\scripts\create-topics.ps1
mvn clean package -DskipTests
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar
python tools\device_simulator.py
```

完整步骤见 **[docs/SETUP.md](docs/SETUP.md)**。

- **TCP**：`9000`（simple-frame + jt808）、`9001`（仅 jt808）
- **HTTP**：`8080`（`/actuator/health`、`/actuator/prometheus`）
- **Kafka**：默认 `localhost:9092`（`docker-compose.yml`）

## 内置协议 `simple-frame`

帧格式：`OMNI`(4) + `bodyLen`(2) + `body`(JSON UTF-8) + `checksum`(1 XOR)

### 1. 鉴权（首包）

```json
{"type":"auth","deviceId":"device-001"}
```

### 2. 上行数据

```json
{"type":"telemetry","payload":{"temp":25}}
```

### 3. 下行 ACK（设备收到平台指令后）

```json
{"type":"ack","messageId":"<与下行指令相同的 messageId>"}
```

## Kafka Topic

| Topic | 方向 |
|-------|------|
| `omni.device.uplink` | 网关 → 业务 |
| `omni.device.lifecycle` | 网关 → 业务（online/offline） |
| `omni.command.downlink` | 业务 → 网关 |
| `omni.command.downlink.result` | 网关 → 业务 |

下行命令示例：

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

## Java 示例（设备 + 业务 Kafka）

最小可运行骨架：[examples/omni-java-skeleton](examples/omni-java-skeleton/README.md)（含 simple-frame / JT808 设备端）

网关收到设备报文时会打 `Protocol recv` / `Uplink parsed` 日志（见 `UplinkDispatchHandler`）。

## 模块

| 模块 | 说明 |
|------|------|
| omni-core | 接口与模型 |
| omni-network | Netty、嗅探、会话、下行调度 |
| omni-protocols | 协议插件（含 simple-frame） |
| omni-bootstrap | Spring Boot 启动与 Kafka |
