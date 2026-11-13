[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://localhost:8081/api/v1',
    [string]$GatewayHealthUrl = 'http://localhost:8081/actuator/health',
    [string]$MailpitBaseUrl = 'http://localhost:8025',
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

function Wait-Until {
    param(
        [Parameter(Mandatory)] [scriptblock]$Action,
        [Parameter(Mandatory)] [scriptblock]$Accept,
        [Parameter(Mandatory)] [string]$FailureMessage
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastError = $null
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $value = & $Action
            if (& $Accept $value) { return $value }
        } catch {
            $lastError = $_
        }
        Start-Sleep -Milliseconds 500
    }
    if ($null -ne $lastError) {
        throw "$FailureMessage Last error: $($lastError.Exception.Message)"
    }
    throw $FailureMessage
}

function Invoke-Json {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Uri,
        [hashtable]$Headers = @{},
        $Body
    )

    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    Invoke-RestMethod @parameters
}

$null = Wait-Until `
    -Action { Invoke-RestMethod -Method Get -Uri $GatewayHealthUrl } `
    -Accept { param($health) $health.status -eq 'UP' } `
    -FailureMessage 'API Gateway did not become healthy.'

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "slice1-$runId@example.com"
$password = 'Slice1-Strong-Password-123!'
$idempotencyKey = "slice1-register-$runId"
$registerBody = @{
    email = $email
    password = $password
    fullName = 'Slice One Guest'
    phone = '0900000000'
}

$challenge = Invoke-Json `
    -Method Post `
    -Uri "$ApiBaseUrl/auth/register" `
    -Headers @{ 'Idempotency-Key' = $idempotencyKey; 'X-Correlation-Id' = "slice1-$runId-register" } `
    -Body $registerBody

$replayedChallenge = Invoke-Json `
    -Method Post `
    -Uri "$ApiBaseUrl/auth/register" `
    -Headers @{ 'Idempotency-Key' = $idempotencyKey; 'X-Correlation-Id' = "slice1-$runId-replay" } `
    -Body $registerBody

if ($challenge.challengeId -ne $replayedChallenge.challengeId) {
    throw 'Registration idempotency returned a different challenge.'
}

$mailQuery = [Uri]::EscapeDataString("to:$email")
$mailBody = Wait-Until `
    -Action { Invoke-RestMethod -Method Get -Uri "$MailpitBaseUrl/view/latest.txt?query=$mailQuery" } `
    -Accept { param($body) $body -match '(?<!\d)\d{6}(?!\d)' } `
    -FailureMessage "OTP email for $email was not delivered to Mailpit."

$otpMatch = [regex]::Match([string]$mailBody, '(?<!\d)\d{6}(?!\d)')
if (-not $otpMatch.Success) { throw 'OTP was not found in the delivered email.' }

$tokens = Invoke-Json `
    -Method Post `
    -Uri "$ApiBaseUrl/auth/register/verify" `
    -Headers @{ 'X-Correlation-Id' = "slice1-$runId-verify" } `
    -Body @{ challengeId = $challenge.challengeId; code = $otpMatch.Value }

$authHeaders = @{
    Authorization = "Bearer $($tokens.accessToken)"
    'X-Correlation-Id' = "slice1-$runId-profile"
}

$profile = Wait-Until `
    -Action { Invoke-Json -Method Get -Uri "$ApiBaseUrl/me" -Headers $authHeaders } `
    -Accept { param($value) $value.email -eq $email } `
    -FailureMessage 'Customer projection was not created from identity.account.activated.v1.'

$updated = Invoke-Json `
    -Method Put `
    -Uri "$ApiBaseUrl/me" `
    -Headers $authHeaders `
    -Body @{
        fullName = 'Slice One Guest Updated'
        phone = '0900000001'
        preferredLanguage = 'vi'
        marketingEmail = $true
        marketingSms = $false
        marketingZalo = $false
        expectedVersion = $profile.version
    }

if ($updated.fullName -ne 'Slice One Guest Updated') {
    throw 'Customer profile update was not persisted.'
}

$null = Invoke-Json `
    -Method Post `
    -Uri "$ApiBaseUrl/auth/logout" `
    -Headers $authHeaders `
    -Body @{ refreshToken = $tokens.refreshToken }

$revokedStatus = $null
try {
    $null = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/me" -Headers $authHeaders
    $revokedStatus = 200
} catch {
    if ($null -ne $_.Exception.Response) {
        $revokedStatus = [int]$_.Exception.Response.StatusCode
    } else {
        throw
    }
}
if ($revokedStatus -ne 401) {
    throw "Revoked access token returned HTTP $revokedStatus instead of 401."
}

[pscustomobject]@{
    Result = 'PASS'
    Email = $email
    IdentityChallengeId = $challenge.challengeId
    CustomerId = $updated.id
    CustomerVersion = $updated.version
    OtpDeliveredVia = 'Mailpit SMTP capture'
    RevokedTokenStatus = $revokedStatus
}
