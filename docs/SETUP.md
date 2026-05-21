# OmniGateway 环境安装与跑通指南

本文说明如何在本地（以 **Windows** 为主，含 macOS/Linux 差异）安装依赖、启动 Kafka、运行网关并完成端到端测试。

---

## 1. 环境要求

| 组件 | 版本建议 | 用途 |
|------|----------|------|
| **JDK** | 17+ | 运行网关 |
| **Maven** | 3.6.3+ | 构建项目 |
| **Python** | 3.8+ | 设备模拟器、压测脚本（可选） |
| **Docker Desktop** | 最新稳定版 | 推荐：一键启动 Kafka |
| **Git** | 任意 | 拉取代码 |

验证命令（PowerShell）：

```powershell
java -version
mvn -version
python --version
docker --version
```

---

## 2. 安装 JDK 与 Maven

### 2.1 JDK 17

1. 安装 [Eclipse Temurin 17](https://adoptium.net/) 或 Oracle JDK 17。
2. 设置环境变量 `JAVA_HOME` 指向安装目录，并把 `%JAVA_HOME%\bin` 加入 `Path`。

### 2.2 Maven

1. 下载 [Apache Maven](https://maven.apache.org/download.cgi) 并解压。
2. 设置 `MAVEN_HOME`，将 `%MAVEN_HOME%\bin` 加入 `Path`。

---

## 3. 安装并启动 Kafka（推荐 Docker）

无需本机单独安装 Kafka/ZooKeeper，使用项目根目录的 `docker-compose.yml`。

### 3.1 启动 Kafka

在项目根目录 `OmniGateway` 下执行：

```powershell
cd d:\projects\private\OmniGateway
docker compose up -d
```

等待容器健康（约 30～60 秒）：

```powershell
docker ps
# 应看到 omni-kafka 状态为 healthy 或 Up
```

### 3.2 创建 Topic

**Windows（PowerShell）：**

```powershell
.\scripts\create-topics.ps1
```

**macOS / Linux：**

```bash
chmod +x scripts/create-topics.sh
./scripts/create-topics.sh
```

将创建以下 Topic（各 3 分区）：

| Topic | 说明 |
|-------|------|
| `omni.device.uplink` | 设备上行数据（网关 → 业务） |
| `omni.device.lifecycle` | 设备上/下线事件 |
| `omni.command.downlink` | 平台下行指令（业务 → 网关） |
| `omni.command.downlink.result` | 下行执行结果（网关 → 业务） |

### 3.3 不用 Docker 时（可选）

- 自行安装 Kafka 3.x，保证 `bootstrap-servers` 为 `localhost:9092`（与 `application.yml` 一致）。
- 使用安装包自带的 `kafka-topics.sh` / `kafka-topics.bat` 创建上表 4 个 Topic。

### 3.4 停止 Kafka

```powershell
docker compose down
```

### 3.5 远程 Kafka / 日志里出现 `localhost:9092`（重要）

`omni.kafka.bootstrap-servers` 只用于**第一次**连上集群；之后客户端会按 Broker 返回的 **advertised address** 建连。

若日志类似：

```text
Connection to node 1 (localhost/127.0.0.1:9092) could not be established
```

说明 **Broker 对外广播的地址仍是 `localhost:9092`**，与你在 `application.yml` 里配的 `42.192.55.235:19092` 无关，需要在 **Kafka 服务端** 修改 `advertised.listeners`。

**Docker Compose 部署在公网机（如 42.192.55.235）时：**

```powershell
$env:KAFKA_ADVERTISED_HOST = "42.192.55.235"
docker compose down
docker compose up -d
.\scripts\create-topics.ps1
```

`docker-compose.yml` 中默认 `KAFKA_ADVERTISED_HOST=127.0.0.1`，仅适合本机访问 `127.0.0.1:19092`。

**裸机 / 安装包 Kafka** 示例（`server.properties`）：

```properties
listeners=PLAINTEXT://0.0.0.0:19092
advertised.listeners=PLAINTEXT://42.192.55.235:19092
```

修改后重启 Broker，再用 `kafka-broker-api-versions.sh --bootstrap-server 42.192.55.235:19092` 验证。

本机网关配置保持：

```yaml
omni.kafka.bootstrap-servers: 42.192.55.235:19092
```

---


## 4. 构建并启动网关

### 4.1 编译

```powershell
cd d:\projects\private\OmniGateway
mvn clean package -DskipTests
```

产物：`omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar`

### 4.2 启动

```powershell
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar
```

成功日志应包含：

- `TCP listener started on port 9000`
- `TCP listener started on port 9001`
- `OmniGateway started (Phase 1 MVP)`

### 4.3 端口说明

| 端口 | 协议 |
|------|------|
| **9000** | simple-frame + JT808（嗅探区分） |
| **9001** | 仅 JT808 |
| **8080** | HTTP 管理（Actuator） |

### 4.4 仅测 TCP、不连 Kafka（可选）

临时关闭 Kafka，避免本地未装 Kafka 时报错：

```powershell
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar `
  --omni.kafka.enabled=false
```

此模式下无法验证上行入 Kafka 与下行，但可测连接、嗅探、鉴权。

---

## 5. 健康检查

另开一个 PowerShell 窗口：

```powershell
# 健康
curl http://localhost:8080/actuator/health

# 指标（Prometheus 格式）
curl http://localhost:8080/actuator/prometheus

# 当前配置版本
curl http://localhost:8080/actuator/omniconfig
```

浏览器可访问：http://localhost:8080/actuator/health

---

## 6. 端到端测试

以下假设 **Kafka 已启动**、**Topic 已创建**、**网关已运行**。

### 6.1 测试一：simple-frame 上行 + 下行

**终端 A — 设备模拟器**

```powershell
cd d:\projects\private\OmniGateway
python tools\device_simulator.py --host 127.0.0.1 --port 9000 --device-id device-001
```

预期：

1. 连接成功
2. 收到 `auth_ok` 类响应（`type: auth_ok`）
3. 周期性发送 `telemetry`（**上行无 TCP 回包**，属正常；数据在 Kafka `omni.device.uplink`）
4. 仅在有平台下行时，模拟器才打印 `<< downlink`；旧版若出现 `reader error: timed out` 为 socket 超时误报，请更新 `tools/device_simulator.py`

网关日志应有 `Device authenticated`；Kafka 正常时可用 Prometheus `omni_messages_uplink_total` 或终端 B 消费 Topic 验证上行。

**终端 B — 消费上行 Topic**

```powershell
docker exec -it omni-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic omni.device.uplink `
  --from-beginning
```

应看到 JSON，含 `deviceId`、`protocol":"simple-frame"`、`messageType` 等。

**终端 C — 消费生命周期（可选）**

```powershell
docker exec -it omni-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic omni.device.lifecycle `
  --from-beginning
```

设备鉴权后应有 `event":"online"`。

**终端 D — 下发下行指令**

将下面 JSON **保存为一行** 或通过 producer 发送（注意 `deviceId` 与模拟器一致）：

```json
{"messageId":"cmd-001","deviceId":"device-001","protocol":"simple-frame","commandType":"setParam","payload":{"key":"interval","value":60},"timeoutMs":5000}
```

PowerShell 发送示例：

```powershell
$json = '{"messageId":"cmd-001","deviceId":"device-001","protocol":"simple-frame","commandType":"setParam","payload":{"key":"interval","value":60},"timeoutMs":5000}'
$json | docker exec -i omni-kafka /opt/kafka/bin/kafka-console-producer.sh `
  --bootstrap-server localhost:9092 `
  --topic omni.command.downlink
```

**终端 E — 查看下行结果**

```powershell
docker exec -it omni-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic omni.command.downlink.result `
  --from-beginning
```

预期：`status":"SUCCESS"`（模拟器会自动回 `ack`）。

终端 A 中应打印收到的下行帧（`<<` 日志）。

---

### 6.2 测试二：JT808 注册与心跳

```powershell
python tools\jt808_device_simulator.py --host 127.0.0.1 --port 9001 --phone 13800138000
```

消费上行：

```powershell
docker exec -it omni-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic omni.device.uplink `
  --from-beginning
```

预期：`protocol":"jt808"`，`messageType` 如 `0x0002`（心跳）。

JT808 下行示例（需设备已在线且 `deviceId` 为手机号）：

```json
{"messageId":"cmd-jt808-1","deviceId":"13800138000","protocol":"jt808","commandType":"REGISTER_ACK","payload":{"answerSerial":1,"result":0},"timeoutMs":5000}
```

---

### 6.3 测试三：指标与连接数

```powershell
curl http://localhost:8080/actuator/prometheus | findstr omni_connections
curl http://localhost:8080/actuator/prometheus | findstr omni_messages_uplink
```

模拟器运行期间 `omni_connections_active` 应 ≥ 1。

---

### 6.4 压测脚本（可选）

```powershell
# 长连接
python tools\loadtest\pt01_hold_connections.py --connections 100 --duration-sec 60

# 上行吞吐（需 Kafka 正常）
python tools\loadtest\pt02_uplink_throughput.py --devices 20 --duration-sec 30
```

压测结果记录模板：[loadtest/BASELINE-REPORT.md](loadtest/BASELINE-REPORT.md)

---

## 7. 配置说明（常用）

主配置：`omni-bootstrap/src/main/resources/application.yml`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `omni.kafka.bootstrap-servers` | `localhost:9092` | Kafka 地址 |
| `omni.kafka.enabled` | `true` | 是否发 Kafka |
| `omni.gateway.listeners[].port` | 9000 / 9001 | TCP 监听 |
| `omni.downlink.topic` | `omni.command.downlink` | 下行 Topic |
| `omni.logging.protocol-hex-enabled` | `false` | 是否打印完整协议帧十六进制（见下） |

### 协议十六进制流量日志

开启后，每条设备 **recv/send** 会输出一行（logger：`com.omni.gateway.protocol.traffic`），格式类似：

```text
2026-05-20 17:55:02.482 --- SESSION: 44d4e18ce9874b4ea985794b3deacf86 SN: device-001 recv 4F 4D 4E 49 ...
```

```yaml
omni:
  logging:
    protocol-hex-enabled: true   # 调试抓包对照时打开；生产建议 false
```

也可通过外部热更文件 `omni.logging.protocol-hex-enabled` 动态开关（`GatewayConfigRefreshService` 会刷新 Properties，新连接立即生效）。

关闭时 **不打印** 上述十六进制行，且不解码复制原始帧（零额外开销）。

覆盖配置（无需改 jar 内文件）：

```powershell
java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar `
  --omni.kafka.bootstrap-servers=192.168.1.10:9092 `
  --omni.node-id=gw-dev-1
```

下行 Consumer Group 名为：`omni-gateway-downlink-<node-id>`，例如 `omni-gateway-downlink.local-8080`。

---

## 8. 故障排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 启动报 Kafka 连接失败 | Kafka 未启动或地址错误 | `docker compose up -d`，检查 19092 |
| bootstrap 正确但仍连 `localhost:9092` | Broker `advertised.listeners` 配成 localhost:9092 | 见 §3.5，设置 `KAFKA_ADVERTISED_HOST` 或 `advertised.listeners` 为公网 IP:19092 |
| 嗅探后断开 | 首包不是 OMNI / 0x7E | 确认端口与协议、见协议文档 |
| 下行 OFFLINE | 设备未鉴权或不在本节点 Session | 先跑模拟器鉴权，再发下行 |
| 下行无 SUCCESS | 未回 ack 或 messageId 不一致 | simple-frame 需回 `{"type":"ack","messageId":"cmd-001"}` |
| 9000/9001 无法连接 | 防火墙或端口占用 | `netstat -ano \| findstr 9000` |
| Maven 编译失败 | JDK/Maven 版本过低 | 使用 JDK17 + Maven 3.6.3+ |

查看网关 JSON 日志中的 `event` 字段：`device_auth`、`uplink_publish` 等。

---

## 9. 自动化测试（无需 Docker）

项目内置 **JUnit + Embedded Kafka**，不依赖本机 Kafka 即可验证核心链路：

```powershell
# 仅跑自动化测试（推荐 CI / 提交前）
.\scripts\run-tests.ps1
# 或
mvn test
```

| 测试类型 | 位置 | 说明 |
|----------|------|------|
| 单元测试 | `omni-protocols`、`omni-network` | 编解码、Session |
| 集成测试 | `SimpleFrameIntegrationTest` | 启动网关(19000) + 内存 Kafka，验证上行/下行 |

集成测试使用端口 **19000**（`application-test.yml`），与开发端口 9000 不冲突。

**手动 E2E（需 Docker + 已启动 jar）：**

```powershell
.\scripts\run-e2e.ps1
```

**保存 Git 进度：**

```powershell
.\scripts\save-progress.ps1
```

---

## 10. 推荐操作顺序（清单）

```
[ ] 安装 JDK 17、Maven、Python、Docker
[ ] docker compose up -d
[ ] .\scripts\create-topics.ps1
[ ] mvn clean package -DskipTests
[ ] java -jar omni-bootstrap\target\omni-bootstrap-1.0.0-SNAPSHOT.jar
[ ] curl http://localhost:8080/actuator/health
[ ] python tools\device_simulator.py
[ ] 消费 omni.device.uplink 有数据
[ ] 生产 omni.command.downlink 下行命令
[ ] 消费 omni.command.downlink.result 为 SUCCESS
```

完成以上步骤即表示**第一期环境跑通**。

---

## 11. 第二期（Redis + 分节点下行）

`docker compose up -d` 已包含 **Redis 6379**。配置见 `omni.session.redis-enabled: true`。

```powershell
.\scripts\create-downlink-topics.ps1 -NodeIds "local-8080"
```

业务下行推荐 Topic：`omni.command.downlink.{nodeId}`（`omni.node-id` 与节点一致）。迁移期可设 `omni.downlink.router-enabled: true` 继续写统一 Topic `omni.command.downlink`。

运维：`POST /actuator/omnidrain`、`GET /actuator/omnilisteners`。详见 [PHASE2.md](PHASE2.md)。

### GB28181（第三期 M18，TCP 5060）

- 默认监听 **5060**，仅启用 `gb28181` 插件（与 JT808 分端口，避免嗅探冲突）。
- 设备 `REGISTER` → 网关 `200 OK` → 会话绑定 20 位 `DeviceID`。
- `MESSAGE` + `MANSCDP+xml`（如 `Keepalive`）→ 上行 Kafka，`messageType` 为 `CmdType`。
- 设计说明：[design/07-gb28181.md](design/07-gb28181.md)、[PHASE3.md](PHASE3.md)。

---

## 12. 相关文档

- [README.md](../README.md) — 项目概览
- [PHASE1-ITERATION.md](PHASE1-ITERATION.md) — 迭代功能说明
- [PHASE2.md](PHASE2.md) — 第二期生产化
- [PHASES.md](PHASES.md) — 分期规划
