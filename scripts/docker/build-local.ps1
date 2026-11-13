[CmdletBinding()]
param(
    [string[]]$Services = @(
        'api-gateway',
        'identity-service',
        'customer-service',
        'room-inventory-service',
        'reservation-service',
        'pricing-service',
        'payment-service',
        'stay-service',
        'hotel-service-catalog',
        'billing-service',
        'review-service',
        'notification-service',
        'reporting-audit-service',
        'ai-conversation-service',
        'frontend'
    )
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'
$env:COMPOSE_PARALLEL_LIMIT = '1'

docker info --format 'Docker Engine {{.ServerVersion}}' | Write-Host
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not responding.'
}

$running = docker compose -f $composeFile --profile observability ps -q `
    --status running
if ($running) {
    throw 'Stop the project first with scripts/docker/stop-local.ps1 before building.'
}

foreach ($service in $Services) {
    Write-Host "Building ${service} with no application containers running"
    docker compose -f $composeFile build $service
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for $service."
    }
}

Write-Host 'Sequential local build completed.'
