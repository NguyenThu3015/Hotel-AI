[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345',
    [ValidateRange(30, 300)]
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

function Get-DotEnvValue([string]$Name) {
    $path = Join-Path $PSScriptRoot '..\..\infrastructure\.env'
    foreach ($line in Get-Content -LiteralPath $path -Encoding utf8) {
        if ($line -match "^\s*$([regex]::Escape($Name))=(.*)$") {
            return $matches[1].Trim()
        }
    }
    return ''
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
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    try {
        Invoke-RestMethod @parameters
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "HTTP $status $Method ${Uri}: $detail"
    }
}

function Wait-Until {
    param(
        [Parameter(Mandatory)] [scriptblock]$Action,
        [Parameter(Mandatory)] [scriptblock]$Accept,
        [Parameter(Mandatory)] [string]$FailureMessage
    )
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try {
            $value = & $Action
            if (& $Accept $value) { return $value }
        } catch {
            # Services and RabbitMQ consumers may still be warming up.
        }
        Start-Sleep -Milliseconds 700
    }
    throw $FailureMessage
}

$secret = Get-DotEnvValue 'CASSO_WEBHOOK_SECRET'
$bankId = Get-DotEnvValue 'CASSO_BANK_ID'
$accountNumber = Get-DotEnvValue 'CASSO_ACCOUNT_NUMBER'
if ([string]::IsNullOrWhiteSpace($secret) -or
    [string]::IsNullOrWhiteSpace($bankId) -or
    [string]::IsNullOrWhiteSpace($accountNumber)) {
    throw 'CASSO_WEBHOOK_SECRET, CASSO_BANK_ID va CASSO_ACCOUNT_NUMBER phai duoc dien trong infrastructure/.env.'
}

$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "casso-smoke-$runId@example.com"
$checkIn = (Get-Date).Date.AddDays(30).ToString('yyyy-MM-dd')
$checkOut = (Get-Date).Date.AddDays(31).ToString('yyyy-MM-dd')

$availability = Invoke-Json -Method Get `
    -Uri "$ApiBaseUrl/availability/search?checkIn=$checkIn&checkOut=$checkOut&adults=1&children=0&rooms=1&sort=PRICE_ASC"
$room = $availability.content |
    Where-Object { $_.availableRooms -gt 0 -and $_.price.pricingStatus -eq 'AVAILABLE' } |
    Select-Object -First 1
if ($null -eq $room) { throw 'Khong co room type san sang de tao booking smoke.' }

$tokens = Invoke-Json -Method Post -Uri "$ApiBaseUrl/auth/login" `
    -Body @{ email = $AdminEmail; password = $AdminPassword }
$auth = @{ Authorization = "Bearer $($tokens.accessToken)" }
$customers = Invoke-Json -Method Get -Uri "$ApiBaseUrl/admin/customers?page=0&size=100" -Headers $auth
$customer = $customers.content | Where-Object { $_.status -eq 'ACTIVE' } | Select-Object -First 1
if ($null -eq $customer) { throw 'Khong co customer ACTIVE de tao booking staff smoke.' }

$reservation = Invoke-Json -Method Post -Uri "$ApiBaseUrl/admin/bookings?customerId=$($customer.id)&channel=STAFF" `
    -Headers ($auth + @{ 'Idempotency-Key' = "casso-smoke-booking-$runId" }) `
    -Body @{
        roomTypeId = $room.roomTypeId
        checkIn = $checkIn
        checkOut = $checkOut
        roomCount = 1
        adults = 1
        children = 0
        contactName = 'Casso Smoke Guest'
        contactEmail = $email
        contactPhone = '0900000000'
        specialRequests = 'CASSO-SMOKE-TEST'
        guests = @(@{
            fullName = 'Casso Smoke Guest'
            documentType = 'PASSPORT'
            documentNumber = "SMOKE-$runId"
            nationality = 'VN'
            primary = $true
        })
    }

# Reservation and payment are separate services; wait for the payment projection
# before creating the intent so this smoke test does not race RabbitMQ delivery.
$null = Wait-Until `
    -Action { Invoke-Json -Method Get -Uri "$ApiBaseUrl/payments?reservationId=$($reservation.id)&size=1" -Headers $auth } `
    -Accept { param($value) $null -ne $value.content } `
    -FailureMessage 'Payment Service chua nhan reservation_payment_projection.'

$deposit = [decimal]$reservation.depositRequired
$amount = if ($deposit -gt 0) { $deposit } else { [decimal]$reservation.totalAmount }
$purpose = if ($deposit -gt 0) { 'DEPOSIT' } else { 'FULL' }
$payment = Invoke-Json -Method Post -Uri "$ApiBaseUrl/payments/intents" `
    -Headers ($auth + @{ 'Idempotency-Key' = "casso-smoke-payment-$runId" }) `
    -Body @{
        reservationId = $reservation.id
        provider = 'CASSO'
        purpose = $purpose
        amount = $amount
        currency = 'VND'
    }
$instructions = Invoke-Json -Method Get -Uri "$ApiBaseUrl/payments/$($payment.id)/bank-transfer" -Headers $auth
if ($instructions.transferContent -notmatch 'PAY-[A-F0-9]{32}') {
    throw 'Casso transfer content khong co ma PAY- duy nhat.'
}

# Casso Webhook V2 signing: timestamp + '.' + recursively key-sorted JSON.
$eventId = "casso-smoke-$runId"
$reference = "SMOKE-$runId"
$transactionDateTime = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
$data = [ordered]@{
    accountNumber = $accountNumber
    amount = [long]$amount
    description = [string]$instructions.transferContent
    id = $eventId
    reference = $reference
    transactionDateTime = $transactionDateTime
}
$payload = [ordered]@{ error = 0; data = $data }
$rawPayload = $payload | ConvertTo-Json -Depth 10 -Compress
$canonicalData = [ordered]@{
    accountNumber = $accountNumber
    amount = [long]$amount
    description = [string]$instructions.transferContent
    id = $eventId
    reference = $reference
    transactionDateTime = $transactionDateTime
}
$canonical = ([ordered]@{ data = $canonicalData; error = 0 } | ConvertTo-Json -Depth 10 -Compress)
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$hmac = [Security.Cryptography.HMACSHA512]::new([Text.Encoding]::UTF8.GetBytes($secret))
try {
    $digest = -join ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$timestamp.$canonical")) |
        ForEach-Object { $_.ToString('x2') })
} finally {
    $hmac.Dispose()
}
$signature = "t=$timestamp,v1=$digest"

$webhook = Invoke-Json -Method Post -Uri "$ApiBaseUrl/payments/webhooks/casso" `
    -Headers @{ 'X-Casso-Signature' = $signature } -Body $payload
if ($webhook.result -ne 'SUCCEEDED' -or $webhook.success -ne $true) {
    throw "Webhook smoke khong thanh cong: $($webhook | ConvertTo-Json -Compress)"
}

$duplicate = Invoke-Json -Method Post -Uri "$ApiBaseUrl/payments/webhooks/casso" `
    -Headers @{ 'X-Casso-Signature' = $signature } -Body $payload
if ($duplicate.replay -ne $true -or $duplicate.result -ne 'SUCCEEDED') {
    throw "Webhook duplicate khong duoc dedupe: $($duplicate | ConvertTo-Json -Compress)"
}

$paymentAfter = Wait-Until `
    -Action { Invoke-Json -Method Get -Uri "$ApiBaseUrl/payments/$($payment.id)" -Headers $auth } `
    -Accept { param($value) $value.status -eq 'SUCCEEDED' } `
    -FailureMessage 'Payment intent khong chuyen sang SUCCEEDED.'
$reservationAfter = Wait-Until `
    -Action { Invoke-Json -Method Get -Uri "$ApiBaseUrl/admin/bookings/$($reservation.id)" -Headers $auth } `
    -Accept { param($value) ([decimal]$value.paidAmount) -ge [decimal]$amount } `
    -FailureMessage 'Reservation chua nhan payment.succeeded.v1.'

[pscustomobject]@{
    Result = 'PASS'
    CustomerEmail = $email
    ReservationId = $reservation.id
    ReservationReference = $reservation.reference
    ReservationStatus = $reservationAfter.status
    PaymentId = $payment.id
    PaymentStatus = $paymentAfter.status
    PaidAmount = $reservationAfter.paidAmount
    TransferContent = $instructions.transferContent
    FirstWebhookResult = $webhook.result
    DuplicateWebhookReplay = $duplicate.replay
}
