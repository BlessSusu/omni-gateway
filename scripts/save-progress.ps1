# 保存当前工作进度到 Git（需已 git init）
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

if (-not (Test-Path .git)) {
    Write-Host "Initializing git repository..."
    git init
}

git add -A
git status

$msg = @"
feat(phase1): OmniGateway MVP with tests and setup docs

- Multi-module gateway: core, network, protocols, bootstrap
- simple-frame and JT808 plugins, Kafka up/down, backpressure
- Docker Compose Kafka, SETUP guide, load test scripts
- Unit tests + Embedded Kafka integration test
"@

git commit -m $msg
Write-Host "Progress saved." -ForegroundColor Green
git log -1 --oneline
