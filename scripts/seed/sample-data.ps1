[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345',
    [ValidateRange(1, 200)] [int]$Count = 50,
    [ValidateRange(0, 100)] [int]$CancelEvery = 10,
    [switch]$WithServices
)

$ErrorActionPreference = 'Stop'
$marker = 'HOTELAI-SAMPLE-DATA-V1'

function Invoke-Json {
    param([string]$Method, [string]$Uri, [hashtable]$Headers = @{}, $Body)
    $args = @{ Method = $Method; Uri = $Uri; Headers = $Headers; ContentType = 'application/json'; TimeoutSec = 45 }
    if ($null -ne $Body) { $args.Body = $Body | ConvertTo-Json -Depth 20 -Compress }
    try { return Invoke-RestMethod @args }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "HTTP $status $Method $Uri`: $detail"
    }
}

$tokenResponse = Invoke-Json POST "$ApiBaseUrl/auth/login" -Body @{ email = $AdminEmail; password = $AdminPassword }
$auth = @{ Authorization = "Bearer $($tokenResponse.accessToken)" }
$customers = Invoke-Json GET "$ApiBaseUrl/admin/customers?page=0&size=100" -Headers $auth
$activeCustomers = @($customers.content | Where-Object { $_.status -eq 'ACTIVE' })
if ($activeCustomers.Count -eq 0) { throw 'No ACTIVE customer found. Create and verify at least one customer first.' }

$services = @()
if ($WithServices) { $services = @(Invoke-Json GET "$ApiBaseUrl/hotel-services" | Where-Object { $_.active }) }
$created = [System.Collections.Generic.List[object]]::new()
$cancelled = [System.Collections.Generic.List[object]]::new()
$failed = [System.Collections.Generic.List[object]]::new()
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

for ($index = 0; $index -lt $Count; $index++) {
    try {
        # The demo catalog contains a mix of capacities; use a safe occupancy
        # so the seed remains repeatable even when a room type is temporarily held.
        $adults = 1
        $children = 0
        $room = @()
        $checkIn = $null
        for ($offset = 2 + $index; $offset -lt 2 + $index + 500 -and $room.Count -eq 0; $offset++) {
            $candidateIn = (Get-Date).Date.AddDays($offset)
            $candidateOut = $candidateIn.AddDays(1)
            $candidateInText = $candidateIn.ToString('yyyy-MM-dd')
            $candidateOutText = $candidateOut.ToString('yyyy-MM-dd')
            $availability = Invoke-Json GET "$ApiBaseUrl/availability/search?checkIn=$candidateInText&checkOut=$candidateOutText&adults=$adults&children=$children&rooms=1&sort=PRICE_ASC"
            $room = @($availability.content | Where-Object { $_.availableRooms -gt 0 -and $_.price.pricingStatus -eq 'AVAILABLE' } | Select-Object -First 1)
            if ($room.Count -gt 0) {
                $checkIn = $candidateInText
                $checkOut = $candidateOutText
            }
        }
        if ($room.Count -eq 0) { throw "No room type available in the next 500 days" }
        $customer = $activeCustomers[$index % $activeCustomers.Count]
        $email = "sample-$runId-$index@example.com"
        $body = @{
            roomTypeId = $room[0].roomTypeId
            checkIn = $checkIn
            checkOut = $checkOut
            roomCount = 1
            adults = $adults
            children = $children
            contactName = "Sample guest $($index + 1)"
            contactEmail = $email
            contactPhone = ('090' + (1000000 + $index).ToString())
            specialRequests = "$marker | cohort=$($index % 5) | budget=$((1 + $index % 4) * 1000000)"
            guests = @(@{ fullName = "Sample guest $($index + 1)"; nationality = 'VN'; primary = $true })
        }
        $booking = Invoke-Json POST "$ApiBaseUrl/admin/bookings?customerId=$($customer.id)&channel=STAFF" `
            -Headers ($auth + @{ 'Idempotency-Key' = "$marker-$runId-$index" }) -Body $body
        $created.Add($booking)

        if ($WithServices -and $services.Count -gt 0 -and ($index % 5 -eq 0)) {
            $service = $services[$index % $services.Count]
            $null = Invoke-Json POST "$ApiBaseUrl/admin/service-orders" -Headers ($auth + @{ 'Idempotency-Key' = "$marker-service-$runId-$index" }) -Body @{
                reservationId = $booking.id
                customerId = $customer.id
                serviceId = $service.id
                quantity = 1 + ($index % 2)
                scheduledAt = "$checkIn`T05:00:00.000Z"
                note = "$marker pre-stay service"
            }
        }

        if ($CancelEvery -gt 0 -and (($index + 1) % $CancelEvery -eq 0)) {
            $cancelledBooking = Invoke-Json POST "$ApiBaseUrl/admin/bookings/$($booking.id)/cancel" `
                -Headers ($auth + @{ 'Idempotency-Key' = "$marker-cancel-$runId-$index" }) `
                -Body @{ expectedVersion = $booking.version; reason = "$marker test cancellation" }
            $cancelled.Add($cancelledBooking)
        }
    } catch {
        $failed.Add([pscustomobject]@{ index = $index; error = $_.Exception.Message })
    }
}

[pscustomobject]@{
    result = if ($failed.Count -eq 0) { 'PASS' } else { 'PARTIAL' }
    marker = $marker
    requested = $Count
    created = $created.Count
    cancelled = $cancelled.Count
    serviceOrders = if ($WithServices) { [math]::Floor($created.Count / 5) } else { 0 }
    failed = $failed
    reservationIds = @($created | ForEach-Object id)
} | ConvertTo-Json -Depth 8
