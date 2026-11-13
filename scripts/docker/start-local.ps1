[CmdletBinding()]
param(
    [switch]$IncludeObservability,
    [ValidateRange(60, 600)]
    [int]$WaitTimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'
$env:COMPOSE_PARALLEL_LIMIT = '2'

function Invoke-Compose {
    param([Parameter(Mandatory)] [string[]]$Arguments)

    & docker compose -f $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed: $($Arguments -join ' ')"
    }
}

function Start-HealthyGroup {
    param(
        [Parameter(Mandatory)] [string]$Label,
        [Parameter(Mandatory)] [string[]]$Services
    )

    Write-Host "Starting ${Label}: $($Services -join ', ')"
    $arguments = @(
        'up', '-d', '--no-deps', '--wait', '--wait-timeout',
        $WaitTimeoutSeconds.ToString()
    ) + $Services
    Invoke-Compose -Arguments $arguments
}

docker info --format 'Docker Engine {{.ServerVersion}}' | Write-Host
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not responding.'
}

if ($IncludeObservability) {
    $env:MANAGEMENT_TRACING_ENABLED = 'true'
}

Start-HealthyGroup -Label 'infrastructure' -Services @(
    'postgres', 'rabbitmq', 'redis', 'mailpit'
)
Start-HealthyGroup -Label 'customer foundation' -Services @(
    'customer-service', 'notification-service'
)
Start-HealthyGroup -Label 'inventory and pricing' -Services @(
    'room-inventory-service', 'pricing-service'
)
Start-HealthyGroup -Label 'business projections' -Services @(
    'payment-service', 'hotel-service-catalog'
)
Start-HealthyGroup -Label 'billing and reporting' -Services @(
    'billing-service', 'reporting-audit-service'
)

Write-Host 'Applying idempotent AI schemas'
Invoke-Compose -Arguments @('up', '-d', '--no-deps', 'ai-schema-migrator')
Invoke-Compose -Arguments @('wait', 'ai-schema-migrator')

Start-HealthyGroup -Label 'AI services' -Services @(
    'ai-conversation-service',
    'ai-recommendation-service',
    'ai-forecasting-service',
    'ai-revenue-service',
    'ai-customer-analytics-service'
)
Start-HealthyGroup -Label 'review' -Services @('review-service')
Start-HealthyGroup -Label 'reservation and stay' -Services @(
    'reservation-service', 'stay-service'
)
Start-HealthyGroup -Label 'identity' -Services @('identity-service')
Start-HealthyGroup -Label 'entry points' -Services @('api-gateway', 'frontend')

if ($IncludeObservability) {
    Write-Host 'Starting optional observability profile'
    & docker compose -f $composeFile --profile observability up -d `
        jaeger otel-collector prometheus grafana
    if ($LASTEXITCODE -ne 0) {
        throw 'Observability profile failed to start.'
    }
}

Invoke-Compose -Arguments @('ps')
Write-Host 'Local stack started with staged resource limits.'
