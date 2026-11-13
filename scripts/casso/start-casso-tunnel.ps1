param(
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envPath = Join-Path $repoRoot 'infrastructure\.env'
$composePath = Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Khong tim thay $envPath"
}

function Get-DotEnvValue([string]$Name) {
    foreach ($line in Get-Content -LiteralPath $envPath -Encoding utf8) {
        if ($line -match "^\s*$([regex]::Escape($Name))=(.*)$") {
            return $matches[1].Trim()
        }
    }
    return ''
}

$authToken = Get-DotEnvValue 'NGROK_AUTHTOKEN'
if ([string]::IsNullOrWhiteSpace($authToken)) {
    throw 'Hay dien NGROK_AUTHTOKEN trong infrastructure/.env truoc khi chay script.'
}

$gatewayHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/actuator/health' -TimeoutSec 10
if ($gatewayHealth.status -ne 'UP') {
    throw 'API Gateway chua san sang tai http://127.0.0.1:8081.'
}

Push-Location $repoRoot
try {
    docker compose --env-file $envPath -f $composePath --profile casso-tunnel up -d --no-deps webhook-ingress
    if ($LASTEXITCODE -ne 0) { throw 'Khong khoi dong duoc webhook-ingress.' }

    docker compose --env-file $envPath -f $composePath --profile casso-tunnel up -d --no-deps ngrok
    if ($LASTEXITCODE -ne 0) { throw 'Khong khoi dong duoc ngrok.' }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $publicUrl = $null
    do {
        Start-Sleep -Seconds 2
        try {
            $tunnels = Invoke-RestMethod -Uri 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 5
            $publicUrl = ($tunnels.tunnels |
                Where-Object { $_.proto -eq 'https' } |
                Select-Object -First 1).public_url
        } catch {
            $publicUrl = $null
        }
    } while ([string]::IsNullOrWhiteSpace($publicUrl) -and (Get-Date) -lt $deadline)

    if ([string]::IsNullOrWhiteSpace($publicUrl)) {
        docker compose --env-file $envPath -f $composePath --profile casso-tunnel logs --tail 80 ngrok
        throw 'Ngrok khong tao duoc HTTPS endpoint trong thoi gian cho.'
    }

    $webhookUrl = "$($publicUrl.TrimEnd('/'))/api/v1/payments/webhooks/casso"
    Write-Output ''
    Write-Output 'CASSO WEBHOOK URL:'
    Write-Output $webhookUrl
    Write-Output ''
    Write-Output 'Dan URL tren vao o Webhook URL trong Casso Flow.'
    Write-Output 'Khong dong container ngrok trong khi Casso can gui webhook.'
} finally {
    Pop-Location
}
