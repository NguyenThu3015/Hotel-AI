[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'

$ids = docker compose -f $composeFile --profile observability ps -q
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not responding.'
}
if (-not $ids) {
    Write-Host 'No project containers are running.'
    exit 0
}

docker stats --no-stream --format `
    'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}' $ids
