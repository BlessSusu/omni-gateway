# Create per-node downlink Kafka topics (Phase 2 C1 routing).
# Usage: .\scripts\create-downlink-topics.ps1 -BootstrapServers 127.0.0.1:19092 -NodeIds node1,node2,node3

param(
    [string]$BootstrapServers = "127.0.0.1:19092",
    [string[]]$NodeIds = @("local-8080")
)

$baseTopic = "omni.command.downlink"
Write-Host "Creating unified topic $baseTopic (optional router) ..."
docker exec omni-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $baseTopic --partitions 6 --replication-factor 1 2>$null

foreach ($id in $NodeIds) {
    $topic = "omni.command.downlink.$id"
    Write-Host "Creating $topic ..."
    docker exec omni-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic $topic --partitions 6 --replication-factor 1
}

Write-Host "Done."
