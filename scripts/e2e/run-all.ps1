[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://localhost:8081/api/v1',
    [string]$GatewayHealthUrl = 'http://localhost:8081/actuator/health',
    [string]$WebBaseUrl = 'http://localhost:3000',
    [string]$MailpitBaseUrl = 'http://localhost:8025'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')

Write-Host '1/4 - Testing registration, OTP, profile and token revocation through Gateway'
& (Join-Path $PSScriptRoot 'slice1-identity-customer-notification-mailpit.ps1') `
    -ApiBaseUrl $ApiBaseUrl `
    -GatewayHealthUrl $GatewayHealthUrl `
    -MailpitBaseUrl $MailpitBaseUrl

Write-Host '2/4 - Validating Docker Compose configuration'
docker compose `
    -f (Join-Path $repoRoot 'infrastructure\docker-compose.microservices.yml') `
    config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose configuration is invalid.'
}

Write-Host '3/4 - Verifying one live contract for every UC01-UC40'
& (Join-Path $PSScriptRoot 'uc01-uc40-contract.ps1') -ApiBaseUrl $ApiBaseUrl

# Reuse one admin token across Playwright workers. Identity Service deliberately
# rate-limits password logins, while the browser suite only needs read scopes.
$e2eEmail = if ($env:E2E_ADMIN_EMAIL) { $env:E2E_ADMIN_EMAIL } else { 'admin@hotelai.local' }
$e2ePassword = if ($env:E2E_ADMIN_PASSWORD) { $env:E2E_ADMIN_PASSWORD } else { 'Admin@12345' }
$loginBody = @{ email = $e2eEmail; password = $e2ePassword } | ConvertTo-Json -Compress
$loginResponse = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" `
    -ContentType 'application/json' -Body $loginBody
$env:E2E_ADMIN_ACCESS_TOKEN = $loginResponse.accessToken

Write-Host '4/4 - Running browser and API tests for UC01-UC40'
Push-Location (Join-Path $repoRoot 'frontend')
try {
    $env:E2E_API_URL = $ApiBaseUrl
    $env:E2E_WEB_URL = $WebBaseUrl
    npm run test:e2e
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'All E2E checks completed.'
