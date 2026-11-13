\set ON_ERROR_STOP on

-- HotelAI synthetic analytics dataset.
-- All primary keys and business references are deterministic, so this file is
-- safe to run repeatedly.  The HOTELAI_AI_DEMO_V2 marker keeps demo records
-- distinguishable from data entered by users during normal testing.

\connect customer_db

BEGIN;

WITH names AS (
    SELECT ARRAY[
        'Nguyễn Minh Anh','Trần Quốc Bảo','Lê Hoàng Châu','Phạm Gia Hân',
        'Hoàng Đức Huy','Võ Khánh Linh','Đặng Nhật Minh','Bùi Ngọc Mai',
        'Đỗ Thanh Nam','Hồ Phương Nhi','Ngô Quang Phúc','Dương Thảo Vy',
        'Lý Tuấn Kiệt','Mai Yến Nhi','Trịnh Hải Đăng','Tạ Thu Trang',
        'Cao Minh Khang','Quách Bảo Ngọc','La Anh Tuấn','Tôn Nữ Diệu Linh'
    ]::text[] AS values
), source AS (
    SELECT
        g,
        (md5('hotelai-ai-demo-customer-' || g::text))::uuid AS id,
        names.values[((g - 1) % array_length(names.values, 1)) + 1] AS full_name
    FROM generate_series(1, 80) AS series(g)
    CROSS JOIN names
)
INSERT INTO customer_profiles(
    id, identity_id, full_name, email, phone, date_of_birth, gender,
    preferred_language, room_preferences, marketing_email, marketing_sms,
    marketing_zalo, email_verified, identity_status, roles_snapshot,
    consent_updated_at, status, created_at, updated_at, version
)
SELECT
    id,
    NULL,
    full_name,
    'demo.ai.guest' || lpad(g::text, 3, '0') || '@example.test',
    '09' || lpad((31000000 + g)::text, 8, '0'),
    date '1970-01-01' + ((g * 173) % 11000),
    CASE g % 3 WHEN 0 THEN 'OTHER' WHEN 1 THEN 'FEMALE' ELSE 'MALE' END,
    CASE WHEN g % 9 = 0 THEN 'en' ELSE 'vi' END,
    jsonb_build_object(
        'marker', 'HOTELAI_AI_DEMO_V2',
        'bed', CASE g % 3 WHEN 0 THEN 'TWIN' WHEN 1 THEN 'KING' ELSE 'QUEEN' END,
        'view', CASE g % 4 WHEN 0 THEN 'CITY' WHEN 1 THEN 'POOL' WHEN 2 THEN 'QUIET' ELSE 'ANY' END,
        'smoking', false,
        'breakfast', g % 5 <> 0,
        'budgetBand', 1 + (g % 4),
        'travelPurpose', CASE g % 4 WHEN 0 THEN 'BUSINESS' WHEN 1 THEN 'FAMILY' WHEN 2 THEN 'COUPLE' ELSE 'LEISURE' END
    )::text,
    g % 3 = 0,
    g % 4 = 0,
    g % 5 = 0,
    true,
    'ACTIVE',
    'CUSTOMER',
    current_timestamp - ((g % 90) * interval '1 day'),
    'ACTIVE',
    current_timestamp - ((365 + g * 3) * interval '1 day'),
    current_timestamp - ((g % 30) * interval '1 day'),
    0
FROM source
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    room_preferences = EXCLUDED.room_preferences,
    marketing_email = EXCLUDED.marketing_email,
    marketing_sms = EXCLUDED.marketing_sms,
    marketing_zalo = EXCLUDED.marketing_zalo,
    updated_at = EXCLUDED.updated_at;

INSERT INTO customer_notes(id, customer_id, note, created_by_identity_id, created_at)
SELECT
    (md5('hotelai-ai-demo-note-' || g::text))::uuid,
    (md5('hotelai-ai-demo-customer-' || g::text))::uuid,
    CASE g % 6
        WHEN 0 THEN 'HOTELAI_AI_DEMO_V2 · Khách thường đi công tác, ưu tiên phòng yên tĩnh và xuất hóa đơn.'
        WHEN 1 THEN 'HOTELAI_AI_DEMO_V2 · Gia đình có trẻ nhỏ, cần chuẩn bị giường phụ và ghế ăn trẻ em.'
        WHEN 2 THEN 'HOTELAI_AI_DEMO_V2 · Dị ứng hải sản; lưu ý khi tư vấn bữa sáng hoặc nhà hàng.'
        WHEN 3 THEN 'HOTELAI_AI_DEMO_V2 · Ưu tiên tầng cao, phòng không hút thuốc và nhận phòng sớm nếu có thể.'
        WHEN 4 THEN 'HOTELAI_AI_DEMO_V2 · Khách thân thiết, thường sử dụng dịch vụ giặt ủi và đưa đón sân bay.'
        ELSE 'HOTELAI_AI_DEMO_V2 · Liên hệ qua email trước; chỉ gọi điện trong trường hợp khẩn cấp.'
    END,
    (md5('hotelai-ai-demo-staff'))::uuid,
    current_timestamp - ((g % 45) * interval '1 day')
FROM generate_series(1, 48) AS series(g)
ON CONFLICT (id) DO NOTHING;

COMMIT;

\connect reporting_audit_db

BEGIN;

-- About 5,000 historical reservations across 18 months.  Demand is denser on
-- weekends and in summer/December so forecast and backtest charts have a real
-- weekly/seasonal signal instead of a flat line.
WITH room_types AS (
    SELECT array_agg(room_type_id ORDER BY code) AS ids
    FROM room_type_facts
    WHERE active AND code IN ('STANDARD','SUPERIOR','DELUXE','TWIN','FAMILY','EXECUTIVE','SUITE','PRESIDENTIAL')
), calendar AS (
    SELECT
        day::date AS stay_day,
        7
        + CASE WHEN extract(isodow FROM day) IN (5, 6, 7) THEN 4 ELSE 0 END
        + CASE WHEN extract(month FROM day) IN (6, 7, 8, 12) THEN 3 ELSE 0 END
        + CASE WHEN extract(day FROM day) IN (1, 15) THEN 2 ELSE 0 END AS arrivals
    FROM generate_series(current_date - 540, current_date - 1, interval '1 day') AS days(day)
), generated AS (
    SELECT
        c.stay_day,
        seq,
        row_number() OVER (ORDER BY c.stay_day, seq) AS n,
        rt.ids
    FROM calendar c
    CROSS JOIN LATERAL generate_series(1, c.arrivals) AS arrivals(seq)
    CROSS JOIN room_types rt
    WHERE array_length(rt.ids, 1) > 0
), shaped AS (
    SELECT
        *,
        ((n - 1) % 80 + 1)::int AS customer_no,
        CASE
            WHEN ((n - 1) % 80 + 1) <= 5 AND n % 4 <> 0 THEN 'CANCELLED'
            WHEN n % 29 = 0 THEN 'NO_SHOW'
            WHEN n % 13 = 0 THEN 'CANCELLED'
            ELSE 'CHECKED_OUT'
        END AS reservation_status,
        (1 + (n % 4))::int AS stay_nights,
        ids[((n - 1) % array_length(ids, 1)) + 1] AS selected_room_type
    FROM generated
)
INSERT INTO reservation_facts(
    reservation_id, reference, customer_id, channel, status, room_type_id,
    check_in, check_out, room_count, currency, total_amount, paid_amount,
    created_at, cancelled_at, updated_at, source_version
)
SELECT
    (md5('hotelai-ai-history-' || stay_day::text || '-' || seq::text))::uuid,
    'AID' || to_char(stay_day, 'YYMMDD') || lpad(seq::text, 2, '0'),
    (md5('hotelai-ai-demo-customer-' || customer_no::text))::uuid,
    CASE n % 5 WHEN 0 THEN 'OTA' WHEN 1 THEN 'ONLINE' WHEN 2 THEN 'PHONE' WHEN 3 THEN 'WALK_IN' ELSE 'STAFF' END,
    reservation_status,
    selected_room_type,
    stay_day,
    stay_day + stay_nights,
    CASE WHEN n % 19 = 0 THEN 2 ELSE 1 END,
    'VND',
    (700000 + ((n - 1) % 8) * 425000) * stay_nights,
    CASE
        WHEN reservation_status = 'CHECKED_OUT' THEN (700000 + ((n - 1) % 8) * 425000) * stay_nights
        WHEN reservation_status = 'NO_SHOW' THEN round(((700000 + ((n - 1) % 8) * 425000) * stay_nights) * 0.3)
        WHEN n % 2 = 0 THEN round(((700000 + ((n - 1) % 8) * 425000) * stay_nights) * 0.1)
        ELSE 0
    END,
    (stay_day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') - ((14 + (n % 75)) * interval '1 day'),
    CASE WHEN reservation_status = 'CANCELLED'
        THEN (stay_day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') - ((1 + (n % 12)) * interval '1 day')
        ELSE NULL END,
    (stay_day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') + (stay_nights * interval '1 day'),
    1
FROM shaped
ON CONFLICT (reservation_id) DO UPDATE SET
    status = EXCLUDED.status,
    total_amount = EXCLUDED.total_amount,
    paid_amount = EXCLUDED.paid_amount,
    cancelled_at = EXCLUDED.cancelled_at,
    updated_at = EXCLUDED.updated_at;

-- Current and future bookings with deliberately varied lead time, payment
-- ratio, channel and cancellation history for UC39 risk scoring.
WITH room_types AS (
    SELECT array_agg(room_type_id ORDER BY code) AS ids
    FROM room_type_facts
    WHERE active AND code IN ('STANDARD','SUPERIOR','DELUXE','TWIN','FAMILY','EXECUTIVE','SUITE','PRESIDENTIAL')
), source AS (
    SELECT g, rt.ids, ((g - 1) % 80 + 1)::int AS customer_no
    FROM generate_series(1, 60) AS series(g)
    CROSS JOIN room_types rt
    WHERE array_length(rt.ids, 1) > 0
)
INSERT INTO reservation_facts(
    reservation_id, reference, customer_id, channel, status, room_type_id,
    check_in, check_out, room_count, currency, total_amount, paid_amount,
    created_at, cancelled_at, updated_at, source_version
)
SELECT
    (md5('hotelai-ai-future-' || g::text))::uuid,
    'AIRISK' || lpad(g::text, 4, '0'),
    (md5('hotelai-ai-demo-customer-' || (CASE WHEN g <= 18 THEN ((g - 1) % 5 + 1) ELSE customer_no END)::text))::uuid,
    CASE g % 5 WHEN 0 THEN 'OTA' WHEN 1 THEN 'ONLINE' WHEN 2 THEN 'UNKNOWN' WHEN 3 THEN 'PHONE' ELSE 'STAFF' END,
    CASE g % 3 WHEN 0 THEN 'PENDING_PAYMENT' WHEN 1 THEN 'PENDING_CONFIRMATION' ELSE 'CONFIRMED' END,
    ids[((g - 1) % array_length(ids, 1)) + 1],
    current_date + (1 + (g % 30)),
    current_date + (2 + (g % 30) + (g % 3)),
    CASE WHEN g % 14 = 0 THEN 2 ELSE 1 END,
    'VND',
    900000 + (g % 8) * 450000,
    CASE g % 4 WHEN 0 THEN 0 WHEN 1 THEN round((900000 + (g % 8) * 450000) * 0.3) WHEN 2 THEN round((900000 + (g % 8) * 450000) * 0.5) ELSE 900000 + (g % 8) * 450000 END,
    current_timestamp - ((5 + (g % 85)) * interval '1 day'),
    NULL,
    current_timestamp - ((g % 24) * interval '1 hour'),
    1
FROM source
ON CONFLICT (reservation_id) DO UPDATE SET
    status = EXCLUDED.status,
    check_in = EXCLUDED.check_in,
    check_out = EXCLUDED.check_out,
    paid_amount = EXCLUDED.paid_amount,
    updated_at = current_timestamp;

-- Successful payments cover normal ranges; a small number of unusual amounts
-- and repeated failures intentionally exercise anomaly detection.
DELETE FROM payment_facts
WHERE reservation_id IN (
    SELECT reservation_id FROM reservation_facts
    WHERE reference LIKE 'AID%' OR reference LIKE 'AIRISK%'
);

DELETE FROM service_usage_facts
WHERE reservation_id IN (
    SELECT reservation_id FROM reservation_facts
    WHERE reference LIKE 'AID%'
);

WITH ranked AS (
    SELECT r.*, row_number() OVER (ORDER BY r.check_in, r.reference) AS rn
    FROM reservation_facts r
    WHERE r.reference LIKE 'AID%' AND r.status IN ('CHECKED_OUT','NO_SHOW')
)
INSERT INTO payment_facts(
    payment_id, reservation_id, amount, refunded_amount, currency, status,
    provider, occurred_at, updated_at
)
SELECT
    (md5('hotelai-ai-payment-' || reservation_id::text))::uuid,
    reservation_id,
    CASE WHEN rn = 1 THEN total_amount * 10 ELSE greatest(paid_amount, round(total_amount * 0.3)) END,
    CASE WHEN rn % 41 = 0 THEN round(greatest(paid_amount, total_amount * 0.3) * 0.25) ELSE 0 END,
    'VND',
    'SUCCEEDED',
    CASE rn % 4 WHEN 0 THEN 'BANK_TRANSFER' WHEN 1 THEN 'CASH' WHEN 2 THEN 'MOMO' ELSE 'CARD' END,
    check_out::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh',
    check_out::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM ranked
WHERE rn % 5 <> 0
ON CONFLICT (payment_id) DO UPDATE SET
    amount = EXCLUDED.amount,
    refunded_amount = EXCLUDED.refunded_amount,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at;

INSERT INTO payment_facts(
    payment_id, reservation_id, amount, refunded_amount, currency, status,
    provider, occurred_at, updated_at
)
SELECT
    (md5('hotelai-ai-failed-payment-' || g::text || '-' || attempt::text))::uuid,
    (md5('hotelai-ai-future-' || g::text))::uuid,
    900000 + (g % 8) * 450000,
    0,
    'VND',
    'FAILED',
    'CARD',
    current_timestamp - ((g + attempt) * interval '1 hour'),
    current_timestamp - ((g + attempt) * interval '1 hour')
FROM generate_series(1, 8) AS bookings(g)
CROSS JOIN generate_series(1, 3) AS attempts(attempt)
ON CONFLICT (payment_id) DO UPDATE SET updated_at = EXCLUDED.updated_at;

WITH candidates AS (
    SELECT r.*, row_number() OVER (ORDER BY r.check_out, r.reference) AS rn
    FROM reservation_facts r
    WHERE r.reference LIKE 'AID%' AND r.status = 'CHECKED_OUT'
)
INSERT INTO service_usage_facts(
    usage_id, stay_id, reservation_id, service_id, service_code, service_name,
    quantity, gross_amount, currency, status, occurred_at, source_revision, updated_at
)
SELECT
    (md5('hotelai-ai-service-usage-' || reservation_id::text))::uuid,
    (md5('hotelai-ai-stay-' || reservation_id::text))::uuid,
    reservation_id,
    (md5('hotelai-ai-service-' || (rn % 6)::text))::uuid,
    (ARRAY['BREAKFAST','LAUNDRY','AIRPORT_TRANSFER','SPA','MINIBAR','LATE_CHECKOUT'])[(rn % 6) + 1],
    (ARRAY['Bữa sáng','Giặt ủi','Đưa đón sân bay','Spa thư giãn','Minibar','Trả phòng muộn'])[(rn % 6) + 1],
    1 + (rn % 3),
    (1 + (rn % 3)) * (ARRAY[150000,120000,350000,550000,180000,300000])[(rn % 6) + 1],
    'VND',
    'POSTED',
    check_out::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh' - interval '8 hours',
    1,
    check_out::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM candidates
WHERE rn % 3 = 0
ON CONFLICT (usage_id) DO UPDATE SET
    gross_amount = EXCLUDED.gross_amount,
    updated_at = EXCLUDED.updated_at;

WITH reviews AS (
    SELECT
        g,
        (md5('hotelai-ai-review-' || g::text))::uuid AS review_id,
        CASE g % 10 WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 3 WHEN 4 THEN 4 WHEN 5 THEN 4 ELSE 5 END AS rating,
        current_timestamp - ((3 + g * 3) * interval '1 day') AS created_at
    FROM generate_series(1, 120) AS series(g)
)
INSERT INTO review_facts(review_id, rating, status, topics, created_at, published_at, updated_at)
SELECT
    review_id,
    rating,
    CASE WHEN g % 17 = 0 THEN 'HIDDEN' WHEN g % 11 = 0 THEN 'PENDING_MODERATION' ELSE 'PUBLISHED' END,
    CASE
        WHEN rating <= 2 THEN '["CLEANLINESS","STAFF","ROOM"]'::jsonb
        WHEN rating = 3 THEN '["VALUE","AMENITIES"]'::jsonb
        ELSE '["SERVICE","LOCATION","ROOM"]'::jsonb
    END,
    created_at,
    CASE WHEN g % 17 <> 0 AND g % 11 <> 0 THEN created_at + interval '2 hours' ELSE NULL END,
    created_at + interval '2 hours'
FROM reviews
ON CONFLICT (review_id) DO UPDATE SET
    rating = EXCLUDED.rating,
    status = EXCLUDED.status,
    topics = EXCLUDED.topics,
    updated_at = EXCLUDED.updated_at;

COMMIT;

\connect review_db

BEGIN;

WITH source AS (
    SELECT
        g,
        (md5('hotelai-ai-review-' || g::text))::uuid AS review_id,
        (md5('hotelai-ai-review-stay-' || g::text))::uuid AS stay_id,
        (md5('hotelai-ai-review-reservation-' || g::text))::uuid AS reservation_id,
        (md5('hotelai-ai-demo-customer-' || (((g - 1) % 80) + 1)::text))::uuid AS customer_id,
        CASE g % 10 WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 3 WHEN 4 THEN 4 WHEN 5 THEN 4 ELSE 5 END AS rating,
        current_timestamp - ((3 + g * 3) * interval '1 day') AS created_at,
        (ARRAY[
            'Phòng sạch sẽ, nhân viên thân thiện và hỗ trợ rất nhanh. Tôi sẽ quay lại.',
            'Vị trí thuận tiện, bữa sáng phong phú và giường ngủ rất thoải mái.',
            'Không gian yên tĩnh, phòng đúng như hình và giá hợp lý.',
            'Dịch vụ nhìn chung tốt nhưng thời gian nhận phòng hơi lâu.',
            'Tiện nghi đủ dùng, minibar cần bổ sung thêm nước uống.',
            'Phòng có tiếng ồn vào ban đêm nên tôi ngủ không ngon.',
            'Nhà tắm chưa sạch, điều hòa hỏng và tôi đã phải gọi lễ tân nhiều lần.',
            'Nhân viên xử lý chậm, tôi muốn quản lý liên hệ để giải quyết khiếu nại.',
            'Có mùi khói thuốc trong phòng dù tôi đã yêu cầu phòng không hút thuốc.',
            'Trải nghiệm tuyệt vời, dịch vụ chu đáo và mọi yêu cầu đều được đáp ứng.'
        ]::text[])[(g % 10) + 1] AS comment,
        (ARRAY[
            'Nguyễn Minh Anh','Trần Quốc Bảo','Lê Hoàng Châu','Phạm Gia Hân',
            'Hoàng Đức Huy','Võ Khánh Linh','Đặng Nhật Minh','Bùi Ngọc Mai'
        ]::text[])[(g % 8) + 1] AS author_name
    FROM generate_series(1, 120) AS series(g)
), classified AS (
    SELECT
        *,
        CASE WHEN rating >= 4 THEN 'POSITIVE' WHEN rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END AS sentiment,
        CASE WHEN rating >= 4 THEN 0.82 WHEN rating = 3 THEN 0.05 ELSE -0.78 END AS sentiment_score,
        CASE WHEN g % 23 = 0 THEN 'CRITICAL' WHEN rating <= 2 THEN 'HIGH' WHEN rating = 3 THEN 'MEDIUM' ELSE 'LOW' END AS urgency,
        CASE WHEN g % 17 = 0 THEN 'HIDDEN' WHEN g % 11 = 0 THEN 'PENDING_MODERATION' ELSE 'PUBLISHED' END AS review_status
    FROM source
)
INSERT INTO stay_review_projections(
    stay_id, reservation_id, customer_id, reservation_reference, contact_name,
    contact_email, status, scheduled_check_in, scheduled_check_out,
    actual_check_out, room_numbers, source_updated_at
)
SELECT
    stay_id,
    reservation_id,
    customer_id,
    'AIREVIEW' || lpad(g::text, 4, '0'),
    author_name,
    'demo.ai.guest' || lpad((((g - 1) % 80) + 1)::text, 3, '0') || '@example.test',
    'CHECKED_OUT',
    (created_at::date - (2 + (g % 3))),
    created_at::date - 1,
    created_at - interval '1 day',
    (300 + (g % 50))::text,
    created_at
FROM classified
ON CONFLICT (stay_id) DO UPDATE SET source_updated_at = EXCLUDED.source_updated_at;

WITH source AS (
    SELECT
        g,
        (md5('hotelai-ai-review-' || g::text))::uuid AS review_id,
        (md5('hotelai-ai-review-stay-' || g::text))::uuid AS stay_id,
        (md5('hotelai-ai-review-reservation-' || g::text))::uuid AS reservation_id,
        (md5('hotelai-ai-demo-customer-' || (((g - 1) % 80) + 1)::text))::uuid AS customer_id,
        CASE g % 10 WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 3 WHEN 4 THEN 4 WHEN 5 THEN 4 ELSE 5 END AS rating,
        current_timestamp - ((3 + g * 3) * interval '1 day') AS created_at,
        (ARRAY[
            'Phòng sạch sẽ, nhân viên thân thiện và hỗ trợ rất nhanh. Tôi sẽ quay lại.',
            'Vị trí thuận tiện, bữa sáng phong phú và giường ngủ rất thoải mái.',
            'Không gian yên tĩnh, phòng đúng như hình và giá hợp lý.',
            'Dịch vụ nhìn chung tốt nhưng thời gian nhận phòng hơi lâu.',
            'Tiện nghi đủ dùng, minibar cần bổ sung thêm nước uống.',
            'Phòng có tiếng ồn vào ban đêm nên tôi ngủ không ngon.',
            'Nhà tắm chưa sạch, điều hòa hỏng và tôi đã phải gọi lễ tân nhiều lần.',
            'Nhân viên xử lý chậm, tôi muốn quản lý liên hệ để giải quyết khiếu nại.',
            'Có mùi khói thuốc trong phòng dù tôi đã yêu cầu phòng không hút thuốc.',
            'Trải nghiệm tuyệt vời, dịch vụ chu đáo và mọi yêu cầu đều được đáp ứng.'
        ]::text[])[(g % 10) + 1] AS comment,
        (ARRAY[
            'Nguyễn Minh Anh','Trần Quốc Bảo','Lê Hoàng Châu','Phạm Gia Hân',
            'Hoàng Đức Huy','Võ Khánh Linh','Đặng Nhật Minh','Bùi Ngọc Mai'
        ]::text[])[(g % 8) + 1] AS author_name
    FROM generate_series(1, 120) AS series(g)
), classified AS (
    SELECT
        *,
        CASE WHEN rating >= 4 THEN 'POSITIVE' WHEN rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END AS sentiment,
        CASE WHEN rating >= 4 THEN 0.82 WHEN rating = 3 THEN 0.05 ELSE -0.78 END AS sentiment_score,
        CASE WHEN g % 23 = 0 THEN 'CRITICAL' WHEN rating <= 2 THEN 'HIGH' WHEN rating = 3 THEN 'MEDIUM' ELSE 'LOW' END AS urgency,
        CASE WHEN g % 17 = 0 THEN 'HIDDEN' WHEN g % 11 = 0 THEN 'PENDING_MODERATION' ELSE 'PUBLISHED' END AS review_status
    FROM source
)
INSERT INTO reviews(
    id, stay_id, reservation_id, customer_id, reservation_reference,
    author_display_name, customer_email, rating, comment, comment_hash, topics,
    moderation_flags, status, verified, idempotency_key, request_hash,
    created_at, updated_at, published_at, version, ai_sentiment,
    ai_sentiment_score, ai_topics, ai_urgency, ai_confidence,
    ai_draft_response, ai_model_version, ai_provider, ai_analyzed_at
)
SELECT
    review_id,
    stay_id,
    reservation_id,
    customer_id,
    'AIREVIEW' || lpad(g::text, 4, '0'),
    author_name,
    'demo.ai.guest' || lpad((((g - 1) % 80) + 1)::text, 3, '0') || '@example.test',
    rating,
    comment,
    md5(comment) || md5(comment || '-hotelai'),
    CASE WHEN rating <= 2 THEN 'CLEANLINESS,STAFF,ROOM' WHEN rating = 3 THEN 'VALUE,AMENITIES' ELSE 'SERVICE,LOCATION,ROOM' END,
    jsonb_build_object('synthetic', true, 'marker', 'HOTELAI_AI_DEMO_V2'),
    review_status,
    true,
    'HOTELAI_AI_DEMO_V2-review-' || g,
    md5('request-' || g::text) || md5('request-' || g::text || '-hotelai'),
    created_at,
    created_at + interval '2 hours',
    CASE WHEN review_status = 'PUBLISHED' THEN created_at + interval '2 hours' ELSE NULL END,
    0,
    sentiment,
    sentiment_score,
    CASE WHEN rating <= 2 THEN '["CLEANLINESS","STAFF","ROOM"]'::jsonb WHEN rating = 3 THEN '["VALUE","AMENITIES"]'::jsonb ELSE '["SERVICE","LOCATION","ROOM"]'::jsonb END,
    urgency,
    CASE WHEN urgency = 'CRITICAL' THEN 0.96 ELSE 0.89 END,
    CASE WHEN sentiment = 'NEGATIVE'
        THEN 'Khách sạn thành thật xin lỗi về trải nghiệm chưa tốt. Quản lý phụ trách sẽ kiểm tra và liên hệ để hỗ trợ bạn sớm nhất.'
        ELSE 'Cảm ơn bạn đã chia sẻ trải nghiệm. Khách sạn rất vui khi được đón tiếp và mong sớm gặp lại bạn.' END,
    'synthetic-review-v2',
    'seed',
    created_at + interval '1 hour'
FROM classified
ON CONFLICT (id) DO UPDATE SET
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment,
    status = EXCLUDED.status,
    ai_sentiment = EXCLUDED.ai_sentiment,
    ai_sentiment_score = EXCLUDED.ai_sentiment_score,
    ai_topics = EXCLUDED.ai_topics,
    ai_urgency = EXCLUDED.ai_urgency,
    ai_confidence = EXCLUDED.ai_confidence,
    ai_draft_response = EXCLUDED.ai_draft_response,
    ai_analyzed_at = EXCLUDED.ai_analyzed_at,
    updated_at = EXCLUDED.updated_at;

COMMIT;

\connect ai_customer_analytics_db

BEGIN;

WITH source AS (
    SELECT
        g,
        (md5('hotelai-ai-review-' || g::text))::uuid AS review_id,
        CASE g % 10 WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 3 WHEN 4 THEN 4 WHEN 5 THEN 4 ELSE 5 END AS rating,
        current_timestamp - ((3 + g * 3) * interval '1 day') AS analyzed_at
    FROM generate_series(1, 120) AS series(g)
)
INSERT INTO review_analyses(
    review_id, sentiment, sentiment_score, topics, urgency, confidence,
    draft_response, model_version, analyzed_at
)
SELECT
    review_id,
    CASE WHEN rating >= 4 THEN 'POSITIVE' WHEN rating = 3 THEN 'NEUTRAL' ELSE 'NEGATIVE' END,
    CASE WHEN rating >= 4 THEN 0.82 WHEN rating = 3 THEN 0.05 ELSE -0.78 END,
    CASE WHEN rating <= 2 THEN '["CLEANLINESS","STAFF","ROOM"]'::jsonb WHEN rating = 3 THEN '["VALUE","AMENITIES"]'::jsonb ELSE '["SERVICE","LOCATION","ROOM"]'::jsonb END,
    CASE WHEN g % 23 = 0 THEN 'CRITICAL' WHEN rating <= 2 THEN 'HIGH' WHEN rating = 3 THEN 'MEDIUM' ELSE 'LOW' END,
    CASE WHEN g % 23 = 0 THEN 0.96 ELSE 0.89 END,
    CASE WHEN rating <= 2
        THEN 'Khách sạn thành thật xin lỗi về trải nghiệm chưa tốt. Quản lý phụ trách sẽ kiểm tra và liên hệ để hỗ trợ bạn sớm nhất.'
        ELSE 'Cảm ơn bạn đã chia sẻ trải nghiệm. Khách sạn rất vui khi được đón tiếp và mong sớm gặp lại bạn.' END,
    'synthetic-review-v2',
    analyzed_at
FROM source
ON CONFLICT (review_id) DO UPDATE SET
    sentiment = EXCLUDED.sentiment,
    sentiment_score = EXCLUDED.sentiment_score,
    topics = EXCLUDED.topics,
    urgency = EXCLUDED.urgency,
    confidence = EXCLUDED.confidence,
    draft_response = EXCLUDED.draft_response,
    model_version = EXCLUDED.model_version,
    analyzed_at = EXCLUDED.analyzed_at;

COMMIT;

\echo 'HOTELAI_AI_DEMO_V2 completed successfully.'
