INSERT INTO floors (id, code, name, level_number, active, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'F01', 'Tầng 1', 1, TRUE, now(), now()),
    ('10000000-0000-0000-0000-000000000002', 'F02', 'Tầng 2', 2, TRUE, now(), now());

INSERT INTO amenities (id, code, name, description, icon_url, active, created_at, updated_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'WIFI', 'Wi-Fi', 'Wi-Fi tốc độ cao miễn phí', NULL, TRUE, now(), now()),
    ('20000000-0000-0000-0000-000000000002', 'AIR_CONDITIONING', 'Điều hòa', 'Điều hòa không khí', NULL, TRUE, now(), now()),
    ('20000000-0000-0000-0000-000000000003', 'BREAKFAST', 'Bữa sáng', 'Bao gồm bữa sáng', NULL, TRUE, now(), now());

INSERT INTO room_types (
    id, code, name, description, max_adults, max_children, max_occupancy,
    size_sqm, bed_description, policy_summary, published, active, created_at, updated_at)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'DELUXE', 'Deluxe',
     'Phòng Deluxe dành cho hai khách, đầy đủ tiện nghi cơ bản.', 2, 1, 3,
     28.00, '1 giường đôi', 'Chính sách giá và hủy được Pricing Service cung cấp khi đặt phòng.',
     TRUE, TRUE, now(), now());

INSERT INTO room_type_amenities (room_type_id, amenity_id)
SELECT '30000000-0000-0000-0000-000000000001', id FROM amenities;

INSERT INTO room_images (id, room_type_id, url, alt_text, media_type, byte_size, display_order, active, created_at, updated_at)
VALUES ('40000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        'https://images.example.invalid/rooms/deluxe-placeholder.webp',
        'Ảnh minh họa phòng Deluxe', 'image/webp', 1024, 0, TRUE, now(), now());

INSERT INTO rooms (
    id, room_number, floor_id, room_type_id, operational_status, active, note, created_at, updated_at)
VALUES
    ('50000000-0000-0000-0000-000000000101', '101', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'AVAILABLE', TRUE, NULL, now(), now()),
    ('50000000-0000-0000-0000-000000000102', '102', '10000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'AVAILABLE', TRUE, NULL, now(), now()),
    ('50000000-0000-0000-0000-000000000201', '201', '10000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'AVAILABLE', TRUE, NULL, now(), now());
