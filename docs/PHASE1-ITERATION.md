# 第一期迭代记录

## 已完成

| 轮次 | 内容 |
|------|------|
| MVP | 四模块、simple-frame、上下行 Kafka、嗅探、Session |
| 迭代 1 | 背压、L0/L1 热更、IP 限流、lifecycle、联调脚本 |
| 迭代 2 | **JT808 骨架**、**JSON 日志**、**PT-02/PT-04**、**基线报告模板** |

## JT808（端口 9001 或 9000 嗅探）

- 消息：注册 `0x0100`、心跳 `0x0002`、终端通用应答 `0x0001`
- 鉴权：注册/鉴权后平台回 `0x8100`
- 下行：`commandType` = `REGISTER_ACK` 或十六进制消息 ID（如 `8900`）
- 模拟器：`python tools/jt808_device_simulator.py --port 9001`

## JSON 日志

- 默认 Logstash JSON 控制台（`logback-spring.xml`）
- 明文：`spring.profiles.active=plain-log`
- MDC 字段：`deviceId`、`protocol`、`channelId`、`event`

## 压测

| 编号 | 脚本 / 文档 |
|------|-------------|
| PT-01 | `tools/loadtest/pt01_hold_connections.py` |
| PT-02 | `tools/loadtest/pt02_uplink_throughput.py` |
| PT-04 | `tools/loadtest/pt04_backpressure_readme.md` |
| PT-07 | `tools/loadtest/pt07_device_capacity.py`（容量，第三期满后推荐） |
| 报告 | `docs/loadtest/BASELINE-REPORT.md` |

第二期脚本：PT-03/05/06（见 [PHASE2.md](./PHASE2.md)）。完整索引见 [design/README.md](./design/README.md)。

## 操作速查

```bash
mvn clean package
java -jar omni-bootstrap/target/omni-bootstrap-1.0.0-SNAPSHOT.jar

python tools/device_simulator.py
python tools/jt808_device_simulator.py --port 9001
```
