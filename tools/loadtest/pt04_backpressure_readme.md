# PT-04：Kafka 背压验证

## 目标

Kafka 发布失败或变慢时，`omni_kafka_publish_backpressure=1` 且连接 `autoRead=false`，进程无 OOM。

## 步骤

1. 启动网关，确认 Kafka 地址指向**不可达**或**故意错误**的 broker：
   ```yaml
   omni.kafka.bootstrap-servers: 127.0.0.1:9999
   omni.kafka.failure-threshold: 2
   ```

2. 运行上行压测：
   ```bash
   python tools/loadtest/pt02_uplink_throughput.py --devices 20 --duration-sec 30
   ```

3. 观察指标：
   ```bash
   curl -s http://localhost:8080/actuator/prometheus | findstr backpressure
   curl -s http://localhost:8080/actuator/prometheus | findstr omni_connections_active
   ```

4. 恢复正确 Kafka 地址并 `POST /actuator/omniconfig`，确认 `backpressure` 回落为 0。

## 通过标准

- 背压指标曾置 1
- JVM 堆稳定，无持续增长
- Kafka 恢复后上行可继续
