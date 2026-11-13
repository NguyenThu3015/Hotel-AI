CREATE TABLE IF NOT EXISTS destinations (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    province VARCHAR(120) NOT NULL,
    region VARCHAR(20) NOT NULL,
    region_label VARCHAR(60) NOT NULL,
    style_tag VARCHAR(80) NOT NULL,
    property_name VARCHAR(200) NOT NULL,
    landmark VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_destination_region CHECK (region IN ('BAC', 'TRUNG', 'NAM'))
);

INSERT INTO destinations (
    code, name, province, region, region_label, style_tag,
    property_name, landmark, description, display_order, active
) VALUES
    ('PHU_QUOC', 'Phú Quốc', 'Kiên Giang', 'NAM', 'Miền Nam', 'Biển đảo',
     'HotelAI Phú Quốc Emerald Bay', 'Bãi Kem · Nam Đảo Phú Quốc',
     'Khu nghỉ dưỡng biệt thự biển nhiệt đới với bãi cát trắng mịn và hồ bơi vô cực hướng hoàng hôn.', 1, TRUE),
    ('DA_NANG', 'Đà Nẵng', 'Bán đảo Sơn Trà', 'TRUNG', 'Miền Trung', 'Biển đảo',
     'HotelAI Đà Nẵng Peninsula Resort', 'Bãi Bắc · Bán đảo Sơn Trà',
     'Tuyệt tác kiến trúc tựa sơn hướng thủy giữa rừng nguyên sinh Sơn Trà và vịnh biển xanh ngọc.', 2, TRUE),
    ('HA_LONG', 'Vịnh Hạ Long', 'Quảng Ninh', 'BAC', 'Miền Bắc', 'Biển đảo',
     'HotelAI Hạ Long Heritage Sanctuary', 'Đảo Tuần Châu · Vịnh Hạ Long',
     'Tầm nhìn toàn cảnh kỳ quan thiên nhiên thế giới với bến du thuyền riêng và bể khoáng nóng.', 3, TRUE),
    ('NHA_TRANG', 'Nha Trang', 'Khánh Hòa', 'TRUNG', 'Miền Trung', 'Biển đảo',
     'HotelAI Nha Trang Cam Ranh Bay', 'Bãi Dài · Vịnh Cam Ranh',
     'Không gian nghỉ dưỡng ven vịnh san hô yên bình kèm tổ hợp trị liệu khoáng nóng chuẩn 5 sao.', 4, TRUE),
    ('DA_LAT', 'Đà Lạt', 'Lâm Đồng', 'TRUNG', 'Miền Trung', 'Săn mây & Núi cao',
     'HotelAI Đà Lạt Pine Valley', 'Hồ Tuyền Lâm · Đà Lạt',
     'Biệt thự cổ điển ẩn mình giữa đồi thông sương mù và mặt hồ Tuyền Lâm tĩnh lặng.', 5, TRUE),
    ('SAPA', 'Sapa', 'Lào Cai', 'BAC', 'Miền Bắc', 'Săn mây & Núi cao',
     'HotelAI Sapa Cloud Sanctuary', 'Thung lũng Mường Hoa · Sapa',
     'Khu nghỉ dưỡng trên mây phóng tầm mắt ra đỉnh Fansipan và ruộng bậc thang Mường Hoa.', 6, TRUE),
    ('HOI_AN', 'Hội An', 'Quảng Nam', 'TRUNG', 'Miền Trung', 'Di sản & Đô thị',
     'HotelAI Hội An Riverside Heritage', 'Phố cổ & Biển An Bàng',
     'Giao hòa nét duyên dáng di sản phố Hội cùng không gian nghỉ dưỡng ven sông Thu Bồn thơ mộng.', 7, TRUE),
    ('HA_NOI', 'Hà Nội', 'Hoàn Kiếm', 'BAC', 'Miền Bắc', 'Di sản & Đô thị',
     'HotelAI Hanoi Opera Palace', 'Phố cổ Hoàn Kiếm · Hà Nội',
     'Khách sạn boutique 5 sao mang phong vị Đông Dương thanh lịch ngay trái tim thủ đô ngàn năm.', 8, TRUE),
    ('HCM', 'TP. Hồ Chí Minh', 'Quận 1', 'NAM', 'Miền Nam', 'Di sản & Đô thị',
     'HotelAI Saigon Grand Landmark', 'Bến Bạch Đằng · Quận 1',
     'Biểu tượng sang trọng ven sông Sài Gòn với Sky Bar tầng thượng và dịch vụ quản gia doanh nhân.', 9, TRUE),
    ('VUNG_TAU', 'Vũng Tàu', 'Bà Rịa - Vũng Tàu', 'NAM', 'Miền Nam', 'Biển đảo',
     'HotelAI Vũng Tàu Hồ Tràm Sanctuary', 'Bờ biển Hồ Tràm · Vũng Tàu',
     'Thiên đường nghỉ dưỡng ven biển gần Sài Gòn với rừng tràm nguyên sơ và biệt thự hồ bơi riêng.', 10, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    province = EXCLUDED.province,
    region = EXCLUDED.region,
    region_label = EXCLUDED.region_label,
    style_tag = EXCLUDED.style_tag,
    property_name = EXCLUDED.property_name,
    landmark = EXCLUDED.landmark,
    description = EXCLUDED.description,
    display_order = EXCLUDED.display_order,
    active = EXCLUDED.active;

ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS destination_code VARCHAR(40) NOT NULL DEFAULT 'PHU_QUOC'
    REFERENCES destinations(code);

CREATE INDEX IF NOT EXISTS idx_room_types_destination
    ON room_types(destination_code, published, active);

-- Gán các hạng phòng hiện có vào các điểm đến Việt Nam đặc trưng
UPDATE room_types SET destination_code = 'PHU_QUOC' WHERE code IN ('PRESIDENTIAL', 'SUITE');
UPDATE room_types SET destination_code = 'DA_NANG' WHERE code IN ('DELUXE', 'FAMILY');
UPDATE room_types SET destination_code = 'NHA_TRANG' WHERE code = 'TWIN';
UPDATE room_types SET destination_code = 'DA_LAT' WHERE code = 'SUPERIOR';
UPDATE room_types SET destination_code = 'SAPA' WHERE code = 'STANDARD';
UPDATE room_types SET destination_code = 'HCM' WHERE code = 'EXECUTIVE';

-- Bổ sung các hạng phòng đặc trưng cho những điểm đến còn lại để đủ 10 điểm đến 3 miền đều có tồn kho thực tế
INSERT INTO room_types (
    id, code, name, description, max_adults, max_children, max_occupancy,
    size_sqm, bed_description, policy_summary, published, active, destination_code, created_at, updated_at
) VALUES
    ('30000000-0000-0000-0000-000000000101', 'HALONG_SUITE', 'Heritage Bay Panorama Suite',
     'Suite ban công kính toàn cảnh hướng vịnh Hạ Long, phòng khách riêng và bồn tắm cẩm thạch.',
     2, 2, 4, 68.00, '1 giường King hướng vịnh',
     'Hủy miễn phí trước giờ nhận phòng 24 giờ; bao gồm bữa sáng thượng hạng và trà chiều.',
     TRUE, TRUE, 'HA_LONG', now(), now()),
    ('30000000-0000-0000-0000-000000000102', 'HOIAN_VILLA', 'Riverside Heritage Pool Villa',
     'Biệt thự hồ bơi riêng bên dòng sông Thu Bồn, kiến trúc mái ngói âm dương và vườn nhiệt đới.',
     4, 2, 6, 115.00, '2 giường King lớn',
     'Hủy miễn phí trước giờ nhận phòng 24 giờ; tặng kèm xe đạp dạo phố cổ và trà thảo mộc.',
     TRUE, TRUE, 'HOI_AN', now(), now()),
    ('30000000-0000-0000-0000-000000000103', 'HANOI_IMPERIAL', 'Indochine Opera Club Suite',
     'Phòng Suite phong cách Đông Dương sang trọng gần Nhà Hát Lớn, đặc quyền Executive Lounge.',
     2, 1, 3, 54.00, '1 giường King cao cấp',
     'Hủy miễn phí trước giờ nhận phòng 24 giờ; bao gồm cocktail tối và nhận phòng ưu tiên.',
     TRUE, TRUE, 'HA_NOI', now(), now()),
    ('30000000-0000-0000-0000-000000000104', 'VUNGTAU_OCEAN', 'Hồ Tràm Oceanfront Sanctuary',
     'Không gian nghỉ dưỡng sát biển Hồ Tràm với hiên tắm nắng riêng và vòi sen ngoài trời.',
     2, 2, 4, 62.00, '1 giường King + Sofa bed',
     'Hủy miễn phí trước giờ nhận phòng 24 giờ; miễn phí sử dụng khu khoáng nóng và hồ bơi tràn bờ.',
     TRUE, TRUE, 'VUNG_TAU', now(), now())
ON CONFLICT (code) DO NOTHING;

-- Gắn tiện nghi tiêu chuẩn cho 4 hạng phòng điểm đến mới
INSERT INTO room_type_amenities (room_type_id, amenity_id)
SELECT rt.id, a.id
FROM room_types rt
CROSS JOIN amenities a
WHERE rt.code IN ('HALONG_SUITE', 'HOIAN_VILLA', 'HANOI_IMPERIAL', 'VUNGTAU_OCEAN')
  AND a.code IN ('WIFI', 'AIR_CONDITIONING', 'BREAKFAST', 'BATHTUB', 'MINIBAR', 'SEA_VIEW', 'CITY_VIEW', 'BUTLER_SERVICE')
ON CONFLICT DO NOTHING;

-- Tạo phòng vật lý tương ứng để có tồn kho sẵn sàng đặt ngay
INSERT INTO rooms (
    id, room_number, floor_id, room_type_id, operational_status, active, note, created_at, updated_at
)
SELECT
    v.room_id::uuid,
    v.room_number,
    (SELECT id FROM floors ORDER BY level_number ASC LIMIT 1),
    rt.id,
    'AVAILABLE',
    TRUE,
    v.note,
    now(),
    now()
FROM (
    VALUES
        ('50000000-0000-0000-0000-000000000801', 'HL-801', 'HALONG_SUITE', 'Vịnh Hạ Long · Tầng cao'),
        ('50000000-0000-0000-0000-000000000802', 'HL-802', 'HALONG_SUITE', 'Vịnh Hạ Long · Tầng cao'),
        ('50000000-0000-0000-0000-000000000803', 'HL-803', 'HALONG_SUITE', 'Vịnh Hạ Long · Panorama'),
        ('50000000-0000-0000-0000-000000000804', 'HA-101', 'HOIAN_VILLA', 'Hội An · Ven sông Thu Bồn'),
        ('50000000-0000-0000-0000-000000000805', 'HA-102', 'HOIAN_VILLA', 'Hội An · Ven sông Thu Bồn'),
        ('50000000-0000-0000-0000-000000000806', 'HN-601', 'HANOI_IMPERIAL', 'Hà Nội · Opera Wing'),
        ('50000000-0000-0000-0000-000000000807', 'HN-602', 'HANOI_IMPERIAL', 'Hà Nội · Opera Wing'),
        ('50000000-0000-0000-0000-000000000808', 'HN-603', 'HANOI_IMPERIAL', 'Hà Nội · Opera Wing'),
        ('50000000-0000-0000-0000-000000000809', 'VT-301', 'VUNGTAU_OCEAN', 'Hồ Tràm · Hướng biển'),
        ('50000000-0000-0000-0000-000000000810', 'VT-302', 'VUNGTAU_OCEAN', 'Hồ Tràm · Hướng biển'),
        ('50000000-0000-0000-0000-000000000811', 'VT-303', 'VUNGTAU_OCEAN', 'Hồ Tràm · Hướng biển')
) AS v(room_id, room_number, type_code, note)
JOIN room_types rt ON rt.code = v.type_code
ON CONFLICT (room_number) DO NOTHING;

-- Tạo snapshot giá cho 4 hạng phòng mới
INSERT INTO room_type_price_snapshots (
    room_type_id, nightly_price, currency, tax_rate, service_fee, pricing_version, effective_at, updated_at
)
SELECT
    rt.id,
    v.nightly_price,
    'VND',
    0.0800,
    150000.00,
    1,
    now(),
    now()
FROM (
    VALUES
        ('HALONG_SUITE', 3200000.00),
        ('HOIAN_VILLA', 4600000.00),
        ('HANOI_IMPERIAL', 2850000.00),
        ('VUNGTAU_OCEAN', 2950000.00)
) AS v(type_code, nightly_price)
JOIN room_types rt ON rt.code = v.type_code
ON CONFLICT (room_type_id) DO NOTHING;

-- Phát sự kiện outbox cho các hạng phòng và phòng mới để đồng bộ sang các service khác
INSERT INTO outbox_events (
    id, aggregate_type, aggregate_id, event_type, correlation_id,
    schema_version, payload, occurred_at, published_at,
    publish_attempts, last_error, version
)
SELECT
    ('51000000-0000-0000-0000-00000000090' || row_number() OVER (ORDER BY rt.code))::uuid,
    'RoomType',
    rt.id,
    'inventory.room-type.created.v1',
    ('51000000-0000-0000-0000-00000000091' || row_number() OVER (ORDER BY rt.code))::uuid,
    1,
    jsonb_build_object(
        'roomTypeId', rt.id,
        'code', rt.code,
        'name', rt.name,
        'maxAdults', rt.max_adults,
        'maxChildren', rt.max_children,
        'maxOccupancy', rt.max_occupancy,
        'amenityIds', COALESCE((
            SELECT jsonb_agg(link.amenity_id ORDER BY link.amenity_id)
            FROM room_type_amenities link
            WHERE link.room_type_id = rt.id
        ), '[]'::jsonb),
        'published', rt.published,
        'active', rt.active,
        'version', rt.version
    ),
    now(), NULL, 0, NULL, 0
FROM room_types rt
WHERE rt.code IN ('HALONG_SUITE', 'HOIAN_VILLA', 'HANOI_IMPERIAL', 'VUNGTAU_OCEAN')
ON CONFLICT (id) DO NOTHING;
