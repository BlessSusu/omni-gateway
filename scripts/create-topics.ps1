# Windows：创建 OmniGateway 所需 Kafka Topic
# 前提：docker compose 已启动，容器名 omni-kafka

$ErrorActionPreference = "Stop"
$Bootstrap = "localhost:9092"
$Topics = @(
    "omni.device.uplink",
    "omni.device.lifecycle",
    "omni.command.downlink",
    "omni.command.downlink.result"
)

Write-Host "Creating topics on $Bootstrap ..."

foreach ($t in $Topics) {
    docker exec omni-kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server $Bootstrap `
        --create `
        --if-not-exists `
        --topic $t `
        --partitions 3 `
        --replication-factor 1
    Write-Host "  OK: $t"
}

Write-Host ""
Write-Host "List topics:"
docker exec omni-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server $Bootstrap --list
