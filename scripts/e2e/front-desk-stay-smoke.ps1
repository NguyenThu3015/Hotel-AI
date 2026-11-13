[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345',
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
function Json($value) { $value | ConvertTo-Json -Depth 20 -Compress }
function Call([string]$Method, [string]$Path, $Body = $null, [hashtable]$Extra = @{}) {
    $allHeaders = @{} + $headers + $Extra
    $params = @{ Method=$Method; Uri="$ApiBaseUrl$Path"; Headers=$allHeaders; ContentType='application/json'; TimeoutSec=30 }
    if ($null -ne $Body) { $params.Body = Json $Body }
    try { Invoke-RestMethod @params }
    catch {
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path failed: $detail"
    }
}
function Wait-For([scriptblock]$Action, [scriptblock]$Accept, [string]$Message) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        try { $value = & $Action; if (& $Accept $value) { return $value } } catch {}
        Start-Sleep -Milliseconds 700
    }
    throw $Message
}

$login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -ContentType 'application/json' `
    -Body (Json @{email=$AdminEmail;password=$AdminPassword})
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$today = (Get-Date).Date.ToString('yyyy-MM-dd')
$tomorrow = (Get-Date).Date.AddDays(1).ToString('yyyy-MM-dd')
$availability = Call GET "/availability/search?checkIn=$today&checkOut=$tomorrow&adults=1&children=0&rooms=1&sort=PRICE_ASC"
$option = @($availability.content | Where-Object { $_.availableRooms -gt 0 -and $_.price.pricingStatus -eq 'AVAILABLE' } | Select-Object -First 1)
if ($option.Count -eq 0) { throw 'No room is available for the front-desk smoke.' }
$customers = Call GET '/admin/customers?page=0&size=100'
$customer = @($customers.content | Where-Object status -eq 'ACTIVE' | Select-Object -First 1)
if ($customer.Count -eq 0) { throw 'No active customer is available.' }

$booking = Call POST "/admin/bookings?customerId=$($customer[0].id)&channel=STAFF" @{
    roomTypeId=$option[0].roomTypeId;checkIn=$today;checkOut=$tomorrow;roomCount=1;adults=1;children=0
    contactName='Front desk E2E guest';contactEmail="frontdesk-$runId@example.com";contactPhone='0900000001'
    specialRequests='FRONT-DESK-STAY-SMOKE'
    guests=@(@{fullName='Front desk E2E guest';nationality='VN';primary=$true})
} @{'Idempotency-Key'="frontdesk-booking-$runId"}

$null = Wait-For { Call GET "/admin/payments?reservationId=$($booking.id)&size=1" } `
    { param($value) $null -ne $value.content } 'Payment projection was not created.'
$payment = Call POST '/admin/payments/cash' @{
    reservationId=$booking.id;purpose='FULL';amount=$booking.totalAmount;currency=$booking.currency;note='Front desk smoke cash'
} @{'Idempotency-Key'="frontdesk-cash-$runId"}
$paidBooking = Wait-For { Call GET "/admin/bookings/$($booking.id)" } `
    { param($value) [decimal]$value.paidAmount -ge [decimal]$value.totalAmount } 'Reservation did not receive the cash payment.'
if ($paidBooking.status -eq 'PENDING_CONFIRMATION') {
    $confirmed = Call POST "/admin/bookings/$($booking.id)/confirm" `
        @{expectedVersion=$paidBooking.version} @{'Idempotency-Key'="frontdesk-confirm-$runId"}
} else {
    $confirmed = $paidBooking
}
$null = Wait-For { Call GET "/admin/stay-reservations?query=$($booking.reference)" } `
    { param($value) @($value | Where-Object { $_.reservationId -eq $booking.id -and $_.status -eq 'CONFIRMED' }).Count -gt 0 } `
    'Stay Service did not receive the confirmed reservation projection.'

$rooms = Call GET '/admin/rooms'
$room = @($rooms | Where-Object { $_.roomTypeId -eq $booking.roomTypeId -and $_.status -eq 'AVAILABLE' } | Select-Object -First 1)
if ($room.Count -eq 0) { throw 'No physical room is available for check-in.' }
$roomId = [string]$room[0].id
$roomNumber = [string]$room[0].roomNumber
$stay = Call POST '/admin/stays/check-ins' @{
    reservationId=$booking.id;roomIds=@($roomId)
    guests=@(@{fullName='Front desk E2E guest';dateOfBirth='1990-01-01';nationality='VN';documentType='CCCD';documentNumber="E2E-$runId";primaryGuest=$true})
    outstandingDepositAcknowledged=$true;earlyCheckInApproved=$true
} @{'Idempotency-Key'="frontdesk-checkin-$runId"}

$catalog = Call GET '/hotel-services'
$service = @($catalog | Where-Object active | Select-Object -First 1)
if ($service.Count -gt 0) {
    $serviceStay = Wait-For { Call GET "/admin/service-stays?query=$($booking.reference)" } `
        { param($value) @($value | Where-Object reservationId -eq $booking.id).Count -gt 0 } 'Catalog did not receive the stay projection.'
    $serviceStayId = @($serviceStay | Where-Object reservationId -eq $booking.id)[0].stayId
    $null = Call POST '/admin/service-usages' @{
        stayId=$serviceStayId;serviceId=$service[0].id;quantity=1;occurredAt=[DateTimeOffset]::UtcNow.ToString('o');performed=$true;reason='Front desk smoke usage'
    } @{'Idempotency-Key'="frontdesk-usage-$runId"}
}

$checkedOut = Call POST "/admin/stays/$($stay.id)/check-out" @{
    incidentals=@();debtOverride=$true;debtOverrideReason='Automated E2E smoke with post-stay settlement'
} @{'Idempotency-Key'="frontdesk-checkout-$runId"}
$roomAfter = Wait-For {
    @((Call GET '/admin/rooms') | Where-Object { [string]$_.id -eq $roomId } | Select-Object -First 1)
} { param($value) $null -ne $value -and @($value).Count -eq 1 -and $value[0].status -eq 'WAITING_CLEANING' } `
    'Room was not released to WAITING_CLEANING.'
$cleanedRoom = Call PATCH "/admin/rooms/$roomId/status" @{
    status='AVAILABLE';reason='Front desk smoke: housekeeping completed';expectedVersion=$roomAfter[0].version
}

[pscustomobject]@{
    result='PASS';reservationId=$booking.id;reference=$booking.reference;paymentId=$payment.id
    stayId=$stay.id;roomNumber=$roomNumber;checkoutStatus=$checkedOut.status
    checkoutRoomStatus=$roomAfter[0].status;housekeepingRoomStatus=$cleanedRoom.status
} | ConvertTo-Json -Depth 5
