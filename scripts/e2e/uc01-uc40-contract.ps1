[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345'
)

$ErrorActionPreference = 'Stop'

function Json($value) { if ($null -eq $value) { return $null }; return ($value | ConvertTo-Json -Depth 20 -Compress) }
function Safe-Detail([string]$content) {
    if ([string]::IsNullOrEmpty($content)) { return '' }
    $redacted = $content `
        -replace '(?i)("accessToken"\s*:\s*")[^"]+', '$1[REDACTED]' `
        -replace '(?i)("refreshToken"\s*:\s*")[^"]+', '$1[REDACTED]'
    return $redacted.Substring(0, [math]::Min(180, $redacted.Length))
}
function Invoke-Contract {
    param([string]$Uc, [string]$Method, [string]$Path, $Body, [int[]]$Allowed = @(200, 201, 202, 204, 400, 403, 409))
    $params = @{ Method = $Method; Uri = "$ApiBaseUrl$Path"; Headers = $headers; TimeoutSec = 45; UseBasicParsing = $true }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = Json $Body }
    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $ok = $Allowed -contains $status -and $status -ne 404 -and $status -lt 500
        return [pscustomobject]@{ UC = $Uc; Status = $status; Result = if ($ok) { 'PASS' } else { 'FAIL' }; Path = $Path; Detail = Safe-Detail $response.Content }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
        return [pscustomobject]@{ UC = $Uc; Status = $status; Result = 'FAIL'; Path = $Path; Detail = $_.Exception.Message }
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -ContentType 'application/json' -Body (Json @{ email = $AdminEmail; password = $AdminPassword })
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$sampleBooking = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/admin/bookings?size=1" -Headers $headers
if (@($sampleBooking.content).Count -eq 0) { throw 'No booking available for payment contract check.' }
$tomorrow = (Get-Date).Date.AddDays(120).ToString('yyyy-MM-dd')
$checkout = (Get-Date).Date.AddDays(122).ToString('yyyy-MM-dd')
$forecastBody = @{ hotel_id = 1; days = 7; history_days = 90 }
$results = [System.Collections.Generic.List[object]]::new()

$checks = @(
    @{u='UC01';m='GET';p='/auth/me'},
    @{u='UC02';m='POST';p='/auth/login';b=@{email=$AdminEmail;password=$AdminPassword}},
    @{u='UC04';m='POST';p='/auth/forgot-password';b=@{email="uc04-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com"}},
    @{u='UC05';m='GET';p='/auth/me'},
    @{u='UC06';m='GET';p="/availability/search?checkIn=$tomorrow&checkOut=$checkout&adults=1&children=0&rooms=1&sort=PRICE_ASC"},
    @{u='UC07';m='GET';p="/availability/search?checkIn=$tomorrow&checkOut=$checkout&adults=1&children=0&rooms=1&sort=CAPACITY_DESC"},
    @{u='UC08';m='GET';p='/catalog/room-types'},
    @{u='UC09';m='GET';p='/bookings?size=5'},
    @{u='UC10';m='GET';p='/admin/bookings?size=5'},
    @{u='UC11';m='GET';p='/admin/bookings?status=PENDING_PAYMENT&size=5'},
    @{u='UC12';m='GET';p='/admin/bookings?checkInFrom=2026-01-01&checkInTo=2099-12-31&size=5'},
    @{u='UC13';m='GET';p='/admin/bookings?status=CANCELLED&size=5'},
    @{u='UC14';m='GET';p="/admin/payments?reservationId=$($sampleBooking.content[0].id)&size=5"},
    @{u='UC15';m='GET';p='/admin/stay-reservations?query='},
    @{u='UC16';m='GET';p='/admin/stay-reservations?query='},
    @{u='UC17';m='GET';p='/admin/service-orders'},
    @{u='UC18';m='GET';p='/admin/service-stays'},
    @{u='UC19';m='GET';p='/admin/invoice-stays?query='},
    @{u='UC20';m='GET';p='/bookings?size=20'},
    @{u='UC21';m='GET';p='/admin/reviews?size=5'},
    @{u='UC22';m='GET';p='/admin/rooms'},
    @{u='UC23';m='GET';p='/admin/pricing/promotions'},
    @{u='UC24';m='GET';p='/housekeeping/tasks'},
    @{u='UC25';m='GET';p='/admin/hotel-services'},
    @{u='UC26';m='GET';p='/admin/pricing/promotions'},
    @{u='UC27';m='GET';p='/admin/customers?query=&size=10'},
    @{u='UC28';m='GET';p='/admin/staff?size=10'},
    @{u='UC29';m='GET';p='/admin/roles'},
    @{u='UC30';m='GET';p='/admin/reports/dashboard?hotelId=1&from=2026-01-01&to=2026-12-31'},
    @{u='UC31';m='GET';p='/notifications'},
    @{u='UC32';m='GET';p='/admin/pricing/policies'},
    @{u='UC33';m='GET';p='/admin/audit-logs?size=10'},
    @{u='UC34';m='POST';p='/ai/chat';b=@{message='Advise a quiet room for two guests and state any missing details.'}},
    @{u='UC35';m='POST';p='/ai/recommendations';b=@{hotel_id=1;check_in=$tomorrow;check_out=$checkout;adults=1;children=0;rooms=1;preferred_amenities=@('WiFi')}},
    @{u='UC36';m='POST';p='/ai/forecast';b=$forecastBody},
    @{u='UC37';m='GET';p='/ai/revenue/constraints'},
    @{u='UC38';m='POST';p='/ai/reviews/analyze';b=@{text='The room was clean and staff were friendly, but breakfast had few choices.'}},
    @{u='UC39';m='POST';p='/ai/risks/bookings/score';b=@{hotel_id=1;horizon_days=180}},
    @{u='UC40';m='POST';p='/ai/staff-assistant/chat';b=@{message='Summarize the process for handling a guest special request.'}},
    @{u='UC03';m='POST';p='/auth/refresh';b=@{refreshToken=$login.refreshToken}}
)

foreach ($check in $checks) { $results.Add((Invoke-Contract -Uc $check.u -Method $check.m -Path $check.p -Body $check.b)) }

$failed = @($results | Where-Object Result -ne 'PASS')
[pscustomobject]@{
    result = if ($failed.Count -eq 0) { 'PASS' } else { 'PARTIAL' }
    passed = @($results | Where-Object Result -eq 'PASS').Count
    failed = $failed.Count
    checks = $results
} | ConvertTo-Json -Depth 8
if ($failed.Count -gt 0) { exit 1 }
