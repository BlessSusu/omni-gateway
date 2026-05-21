# 手动 E2E：需先启动 docker Kafka + 网关（见 docs/SETUP.md）
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

function Test-Port($port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
        $c.Close()
        return $true
    } catch { return $false }
}

if (-not (Test-Port 9092)) {
    Write-Host "Kafka 9092 未监听，请先: docker compose up -d" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Port 9000)) {
    Write-Host "网关 9000 未监听，请先启动 omni-bootstrap jar" -ForegroundColor Yellow
    exit 1
}

Write-Host "Running device simulator (15s)..." -ForegroundColor Cyan
$job = Start-Job { Set-Location $using:PWD; python tools/device_simulator.py 2>&1 }
Start-Sleep -Seconds 3

Write-Host "Check uplink topic (timeout 8s)..." -ForegroundColor Cyan
docker exec omni-kafka /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic omni.device.uplink `
    --from-beginning `
    --max-messages 1 `
    --timeout-ms 8000 2>&1

Stop-Job $job -ErrorAction SilentlyContinue
Remove-Job $job -ErrorAction SilentlyContinue
Write-Host "E2E smoke done. Full flow: docs/SETUP.md section 6" -ForegroundColor Green
