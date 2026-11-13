[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8081/api/v1',
    [string]$AdminEmail = 'admin@hotelai.local',
    [string]$AdminPassword = 'Admin@12345',
    [string]$AdminAccessToken = ''
)

$ErrorActionPreference = 'Stop'

function JsonBytes($value) {
    $json = $value | ConvertTo-Json -Depth 20 -Compress
    # Prevent PowerShell 5.1 from unrolling byte[] into individual integers.
    return ,([Text.Encoding]::UTF8.GetBytes($json))
}

function Call([string]$Method, [string]$Path, $Body = $null, [hashtable]$Extra = @{}) {
    $requestHeaders = @{} + $script:headers + $Extra
    $params = @{
        Method = $Method
        Uri = "$ApiBaseUrl$Path"
        Headers = $requestHeaders
        TimeoutSec = 45
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = JsonBytes $Body
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        $detail = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($detail) -and $_.Exception.Response) {
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $detail = $reader.ReadToEnd()
            } catch {}
        }
        if ([string]::IsNullOrWhiteSpace($detail)) { $detail = $_.Exception.Message }
        throw "$Method $Path failed: $detail"
    }
}

if ([string]::IsNullOrWhiteSpace($AdminAccessToken)) {
    $login = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" `
        -ContentType 'application/json; charset=utf-8' `
        -Body (JsonBytes @{email=$AdminEmail;password=$AdminPassword})
    $AdminAccessToken = $login.accessToken
}
$script:headers = @{Authorization="Bearer $AdminAccessToken"}

$amenityBlueprints = @(
    @{code='MINIBAR';name='Minibar';description='Minibar và nước uống trong phòng.'},
    @{code='WORK_DESK';name='Bàn làm việc';description='Không gian làm việc riêng trong phòng.'},
    @{code='BATHTUB';name='Bồn tắm';description='Phòng tắm riêng có bồn tắm.'},
    @{code='BALCONY';name='Ban công';description='Ban công riêng thoáng mát.'},
    @{code='CITY_VIEW';name='Hướng thành phố';description='Tầm nhìn hướng trung tâm thành phố.'},
    @{code='LIVING_ROOM';name='Phòng khách';description='Khu vực phòng khách tách biệt.'},
    @{code='KITCHENETTE';name='Bếp nhỏ';description='Bếp nhỏ phục vụ kỳ nghỉ dài ngày.'},
    @{code='BUTLER_SERVICE';name='Dịch vụ quản gia';description='Hỗ trợ quản gia theo yêu cầu.'}
)

$typeBlueprints = @(
    @{code='STANDARD';name='Standard';description='Phòng tiêu chuẩn gọn gàng, phù hợp khách công tác hoặc kỳ nghỉ ngắn.';adults=2;children=1;occupancy=3;size=24;bed='1 giường Queen';price=850000;amenities=@('WIFI','AIR_CONDITIONING','WORK_DESK');image='https://images.unsplash.com/photo-1566665797739-1674de7a421a'},
    @{code='SUPERIOR';name='Superior';description='Phòng Superior rộng rãi, tiện nghi nâng cấp và tầm nhìn thoáng.';adults=2;children=1;occupancy=3;size=30;bed='1 giường King';price=1100000;amenities=@('WIFI','AIR_CONDITIONING','MINIBAR','CITY_VIEW');image='https://images.unsplash.com/photo-1611892440504-42a792e24d32'},
    @{code='DELUXE';name='Deluxe';description='Phòng Deluxe tiện nghi cao cấp dành cho hai khách.';adults=2;children=1;occupancy=3;size=35;bed='1 giường King';price=1400000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','MINIBAR','BATHTUB');image='https://images.unsplash.com/photo-1618773928121-c32242e63f39'},
    @{code='TWIN';name='Deluxe Twin';description='Phòng hai giường riêng, phù hợp bạn bè hoặc khách công tác.';adults=2;children=1;occupancy=3;size=34;bed='2 giường đơn';price=1350000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','WORK_DESK');image='https://images.unsplash.com/photo-1590490360182-c33d57733427'},
    @{code='FAMILY';name='Family Suite';description='Phòng gia đình có không gian sinh hoạt rộng và sức chứa lớn.';adults=3;children=2;occupancy=5;size=48;bed='1 giường King và 2 giường đơn';price=2200000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','LIVING_ROOM','KITCHENETTE');image='https://images.unsplash.com/photo-1578683010236-d716f9a3f461'},
    @{code='EXECUTIVE';name='Executive';description='Phòng Executive dành cho khách doanh nhân, có bàn làm việc và quyền lợi cao cấp.';adults=2;children=1;occupancy=3;size=42;bed='1 giường King';price=2600000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','MINIBAR','WORK_DESK','CITY_VIEW');image='https://images.unsplash.com/photo-1582719478250-c89cae4dc85b'},
    @{code='SUITE';name='HotelAI Suite';description='Suite cao cấp với phòng khách riêng, bồn tắm và ban công.';adults=3;children=2;occupancy=5;size=65;bed='1 giường King và sofa bed';price=3900000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','MINIBAR','BATHTUB','BALCONY','LIVING_ROOM');image='https://images.unsplash.com/photo-1591088398332-8a7791972843'},
    @{code='PRESIDENTIAL';name='Presidential Suite';description='Hạng phòng cao nhất với không gian lớn, tầm nhìn đẹp và dịch vụ quản gia.';adults=4;children=2;occupancy=6;size=110;bed='2 giường King';price=7500000;amenities=@('WIFI','AIR_CONDITIONING','BREAKFAST','MINIBAR','BATHTUB','BALCONY','CITY_VIEW','LIVING_ROOM','KITCHENETTE','BUTLER_SERVICE');image='https://images.unsplash.com/photo-1600210492486-724fe5c67fb0'}
)

$createdAmenities = 0
$createdFloors = 0
$createdTypes = 0
$createdRates = 0
$createdImages = 0
$createdRooms = 0

$amenities = Call GET '/admin/amenities'
foreach ($blueprint in $amenityBlueprints) {
    if (@($amenities | Where-Object { [string]$_.code -eq [string]$blueprint.code }).Count -eq 0) {
        $null = Call POST '/admin/amenities' @{
            code=$blueprint.code;name=$blueprint.name;description=$blueprint.description;iconUrl=$null
        }
        $createdAmenities++
    }
}
$amenities = Call GET '/admin/amenities'

$floors = Call GET '/admin/floors'
foreach ($level in 3..7) {
    $code = 'F{0:D2}' -f $level
    if (@($floors | Where-Object { [string]$_.code -eq $code }).Count -eq 0) {
        $null = Call POST '/admin/floors' @{code=$code;name="Tầng $level";levelNumber=$level}
        $createdFloors++
    }
}
$floors = Call GET '/admin/floors'

$types = Call GET '/admin/room-types'
foreach ($blueprint in $typeBlueprints) {
    $type = @($types | Where-Object { [string]$_.code -eq [string]$blueprint.code } | Select-Object -First 1)
    if ($type.Count -eq 0) {
        $amenityIds = @($amenities | Where-Object { $blueprint.amenities -contains $_.code } | ForEach-Object id)
        $created = Call POST '/admin/room-types' @{
            code=$blueprint.code;name=$blueprint.name;description=$blueprint.description
            maxAdults=$blueprint.adults;maxChildren=$blueprint.children;maxOccupancy=$blueprint.occupancy
            sizeSqm=$blueprint.size;bedDescription=$blueprint.bed;published=$true;amenityIds=$amenityIds
        }
        $type = @($created)
        $createdTypes++
        $types += $created
    }

    $rates = Call GET "/admin/pricing/base-rates?roomTypeId=$($type[0].id)"
    if (@($rates).Count -eq 0) {
        $rateBody = @{
            roomTypeId=$type[0].id;amount=$blueprint.price
            stayFrom=(Get-Date).Date.ToString('yyyy-MM-dd');stayTo='2099-12-31'
            bookingEffectiveFrom=[DateTimeOffset]::UtcNow.ToString('o');bookingEffectiveTo=$null
            outlierConfirmed=$true
        }
        $rateDeadline = [DateTimeOffset]::UtcNow.AddSeconds(60)
        while ($true) {
            try {
                $null = Call POST '/admin/pricing/base-rates' $rateBody `
                    @{'Idempotency-Key'="room-seed-rate-$($blueprint.code.ToLower())-v1"}
                break
            } catch {
                if ($_.Exception.Message -notmatch 'room_type_reference_not_found' -or
                    [DateTimeOffset]::UtcNow -ge $rateDeadline) {
                    throw
                }
                Start-Sleep -Milliseconds 750
            }
        }
        $createdRates++
    }

    # The room-type list already includes images, avoiding an extra round trip.
    $images = @($type[0].images)
    if ($images.Count -eq 0) {
        $null = Call POST "/admin/room-types/$($type[0].id)/images" @{
            url=$blueprint.image;altText="Phòng $($blueprint.name) tại HotelAI"
            mediaType='image/jpeg';byteSize=350000;displayOrder=0
        }
        $createdImages++
    }
}

$types = Call GET '/admin/room-types'
$rooms = Call GET '/admin/rooms'
$typeSequence = @()
$typeSequence += @('STANDARD') * 10
$typeSequence += @('SUPERIOR') * 8
$typeSequence += @('DELUXE') * 8
$typeSequence += @('TWIN') * 6
$typeSequence += @('FAMILY') * 6
$typeSequence += @('EXECUTIVE') * 5
$typeSequence += @('SUITE') * 5
$typeSequence += @('PRESIDENTIAL') * 2

$roomIndex = 0
foreach ($level in 3..7) {
    $floorCode = 'F{0:D2}' -f $level
    $floor = @($floors | Where-Object { [string]$_.code -eq $floorCode } | Select-Object -First 1)[0]
    foreach ($slot in 1..10) {
        $roomNumber = '{0}{1:D2}' -f $level, $slot
        $typeCode = $typeSequence[$roomIndex]
        $type = @($types | Where-Object { [string]$_.code -eq $typeCode } | Select-Object -First 1)[0]
        if (@($rooms | Where-Object { [string]$_.roomNumber -eq $roomNumber }).Count -eq 0) {
            $created = Call POST '/admin/rooms' @{
                roomNumber=$roomNumber;floorId=$floor.id;roomTypeId=$type.id
                note="HOTELAI_ROOM_SEED_V1 · $typeCode"
            }
            $rooms += $created
            $createdRooms++
        }
        $roomIndex++
    }
}

$finalTypes = Call GET '/admin/room-types'
$finalRooms = Call GET '/admin/rooms'
$finalFloors = Call GET '/admin/floors'
$finalAmenities = Call GET '/admin/amenities'
$seededRooms = @($finalRooms | Where-Object { $_.note -like 'HOTELAI_ROOM_SEED_V1*' })

[pscustomobject]@{
    result='PASS'
    created=@{
        amenities=$createdAmenities;floors=$createdFloors;roomTypes=$createdTypes
        baseRates=$createdRates;images=$createdImages;rooms=$createdRooms
    }
    totals=@{
        roomTypes=$finalTypes.Count;rooms=$finalRooms.Count;seededRooms=$seededRooms.Count
        floors=@($finalFloors).Count;amenities=@($finalAmenities).Count
    }
    roomTypes=@($finalTypes | Sort-Object code | ForEach-Object {
        [pscustomobject]@{code=$_.code;name=$_.name;maxOccupancy=$_.maxOccupancy}
    })
} | ConvertTo-Json -Depth 8
