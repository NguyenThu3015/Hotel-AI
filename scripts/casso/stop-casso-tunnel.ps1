$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envPath = Join-Path $repoRoot 'infrastructure\.env'
$composePath = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'

Push-Location $repoRoot
try {
    docker compose --env-file $envPath -f $composePath --profile casso-tunnel stop ngrok webhook-ingress
    if ($LASTEXITCODE -ne 0) { throw 'Khong dung duoc Casso tunnel.' }
} finally {
    Pop-Location
}
