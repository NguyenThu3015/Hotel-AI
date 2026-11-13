[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'

docker compose -f $composeFile --profile observability stop
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose could not stop the local stack.'
}

Write-Host 'Containers stopped. Volumes and data were preserved.'
