[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://localhost:8081/api/v1',
    [string]$GatewayHealthUrl = 'http://localhost:8081/actuator/health',
    [string]$MailpitBaseUrl = 'http://localhost:8025',
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'
$envFile = Join-Path $repoRoot 'infrastructure\.env'
$sliceScript = Join-Path $PSScriptRoot 'slice1-identity-customer-notification.ps1'
$managedVariables = @('SMTP_HOST', 'SMTP_PORT', 'SMTP_AUTH', 'SMTP_STARTTLS')
$originalValues = @{}

foreach ($name in $managedVariables) {
    $originalValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Invoke-Compose {
    param([Parameter(Mandatory)] [string[]]$Arguments)

    & docker compose --env-file $envFile -f $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed: $($Arguments -join ' ')"
    }
}

function Wait-NotificationHealthy {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $containerId = & docker compose --env-file $envFile -f $composeFile ps -q notification-service
        if ($LASTEXITCODE -eq 0 -and $containerId) {
            $status = & docker inspect $containerId --format '{{.State.Health.Status}}'
            if ($status -eq 'healthy') { return }
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw 'Notification Service did not become healthy.'
}

function Restore-ProcessEnvironment {
    foreach ($name in $managedVariables) {
        $value = $originalValues[$name]
        if ($null -eq $value) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

try {
    # The E2E test uses a synthetic @example.com recipient. Route it to Mailpit
    # deterministically even when infrastructure/.env configures Gmail.
    $env:SMTP_HOST = 'mailpit'
    $env:SMTP_PORT = '1025'
    $env:SMTP_AUTH = 'false'
    $env:SMTP_STARTTLS = 'false'
    Invoke-Compose -Arguments @('up', '-d', '--no-deps', '--force-recreate', 'notification-service')
    Wait-NotificationHealthy

    & $sliceScript `
        -ApiBaseUrl $ApiBaseUrl `
        -GatewayHealthUrl $GatewayHealthUrl `
        -MailpitBaseUrl $MailpitBaseUrl `
        -TimeoutSeconds $TimeoutSeconds
} finally {
    Restore-ProcessEnvironment
    Invoke-Compose -Arguments @('up', '-d', '--no-deps', '--force-recreate', 'notification-service')
    Wait-NotificationHealthy
}
