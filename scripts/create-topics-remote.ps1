
param(
    [string]$Bootstrap = "127.0.0.1:19092"
)

$ErrorActionPreference = "Stop"
$Topics = @(
    "omni.device.uplink",
    "omni.device.lifecycle",
    "omni.command.downlink",
    "omni.command.downlink.result"
)

Write-Host "Creating topics on $Bootstrap (via kafka docker image) ..."

foreach ($t in $Topics) {
    docker run --rm apache/kafka:3.7.0 /opt/kafka/bin/kafka-topics.sh `
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
docker run --rm apache/kafka:3.7.0 /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server $Bootstrap --list
