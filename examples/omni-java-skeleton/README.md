# OmniGateway Java 最小骨架

两个独立 `main`，演示 **设备 TCP** 与 **业务 Kafka** 如何对接网关（不依赖 Spring）。

| 类 | 角色 |
|----|------|
| `SimpleFrameDeviceClient` | 设备：连 `9000`，`auth` + 周期 `telemetry`，自动 `ack` 下行 |
| `Jt808DeviceClient` | 设备：连 `9001`，注册 `0x0100` + 心跳 `0x0002`，自动 `0x0001` 应答平台 |
| `BusinessSkeletonMain` | 业务：消费 uplink / lifecycle / downlink.result，可选发下行 |

## 构建

```powershell
cd D:\projects\private\OmniGateway\examples\omni-java-skeleton
mvn -q package
```

产物：`target/omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar`

## 运行前

1. Kafka 与 Topic 已就绪（见项目根 [docs/SETUP.md](../../docs/SETUP.md)）
2. OmniGateway 已启动（`9000` + Kafka 配置正确）

## 终端 A — 设备

```powershell
java -cp target\omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar `
  com.omni.examples.device.SimpleFrameDeviceClient `
  --host 127.0.0.1 --port 9000 --device-id device-001 --interval 30
```

## 终端 A2 — JT808 设备（端口 9001）

```powershell
java -cp target\omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar `
  com.omni.examples.device.Jt808DeviceClient `
  --host 127.0.0.1 --port 9001 --phone 13800138000 --verbose
```

`deviceId` 与 Kafka 下行中的 `deviceId` 使用同一手机号。下行示例：

```json
{"messageId":"cmd-jt-001","deviceId":"13800138000","protocol":"jt808","commandType":"0x8900","payload":{"bodyHex":""},"timeoutMs":5000}
```

## 终端 B — 业务（只消费）

```powershell
java -cp target\omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar `
  com.omni.examples.business.BusinessSkeletonMain `
  --bootstrap 127.0.0.1:19092
```

远程 Kafka 将 `127.0.0.1:19092` 改为你的地址，例如 `42.192.55.235:19092`。

## 终端 B — 业务（等设备 online 后自动发一条下行）

```powershell
java -cp target\omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar `
  com.omni.examples.business.BusinessSkeletonMain `
  --bootstrap 127.0.0.1:19092 `
  --send-downlink device-001
```

预期：

- `[lifecycle]` 出现 `"event":"online"`
- `[uplink]` 出现 `messageType":"telemetry"`
- 设备终端 `<< downlink` / `>> ack`
- `[downlink.result]` 含 `"status":"SUCCESS"`

## 参数

**设备**

| 参数 | 默认 |
|------|------|
| `--host` | `127.0.0.1` |
| `--port` | `9000` |
| `--device-id` | `device-001` |
| `--interval` | `30`（秒） |
| `--verbose` / `-v` | 打印完整协议 JSON，并输出帧十六进制 |

**业务**

| 参数 | 默认 |
|------|------|
| `--bootstrap` | `127.0.0.1:19092` |
| `--group` | 随机 `omni-business-skeleton-...` |
| `--send-downlink` | 无；指定 `deviceId` 时自动下发一条 `setParam` |
| `--verbose` / `-v` | 格式化打印 Kafka 消息及解析字段 |

收到消息时会打印 `==========` 分隔的协议详情（TCP 为 simple-frame 字段，Kafka 为 ThingModel / lifecycle / downlink.result）。

## 扩展建议

- 将 `ONLINE` 集合持久化到你的设备状态服务
- 用同一 `ObjectMapper` 解析 `ThingModel` / `DownlinkResult` 为强类型 POJO
- 生产环境为每个 Topic 使用固定 `group.id` 与错误处理 / 重试
