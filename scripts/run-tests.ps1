# 运行全部自动化测试（无需 Docker Kafka，集成测试使用 Embedded Kafka）
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

Write-Host "=== OmniGateway: mvn test ===" -ForegroundColor Cyan
mvn test
if ($LASTEXITCODE -ne 0) {
    Write-Host "TEST FAILED" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "=== ALL TESTS PASSED ===" -ForegroundColor Green
