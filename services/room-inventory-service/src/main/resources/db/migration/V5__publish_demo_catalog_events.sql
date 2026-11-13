-- The demo catalog is inserted directly by V2, so no domain events were emitted.
-- Publish snapshots once so event-driven consumers (especially Stay Service) can
-- build the same room and room-type projections as they do for API-created data.
INSERT INTO outbox_events (
    id, aggregate_type, aggregate_id, event_type, correlation_id,
    schema_version, payload, occurred_at, published_at,
    publish_attempts, last_error, version
) SELECT
    '51000000-0000-0000-0000-000000000001',
    'RoomType',
    '30000000-0000-0000-0000-000000000001',
    'inventory.room-type.created.v1',
    '51000000-0000-0000-0000-000000000011',
    1,
    jsonb_build_object(
        'roomTypeId', room_type.id,
        'code', room_type.code,
        'name', room_type.name,
        'maxAdults', room_type.max_adults,
        'maxChildren', room_type.max_children,
        'maxOccupancy', room_type.max_occupancy,
        'amenityIds', COALESCE((
            SELECT jsonb_agg(link.amenity_id ORDER BY link.amenity_id)
            FROM room_type_amenities link
            WHERE link.room_type_id = room_type.id
        ), '[]'::jsonb),
        'published', room_type.published,
        'active', room_type.active,
        'version', room_type.version
    ),
    now(), NULL, 0, NULL, 0
FROM room_types room_type
WHERE room_type.id = '30000000-0000-0000-0000-000000000001'
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events (
    id, aggregate_type, aggregate_id, event_type, correlation_id,
    schema_version, payload, occurred_at, published_at,
    publish_attempts, last_error, version
)
SELECT
    CASE room.id
        WHEN '50000000-0000-0000-0000-000000000101'::uuid
            THEN '51000000-0000-0000-0000-000000000101'::uuid
        WHEN '50000000-0000-0000-0000-000000000102'::uuid
            THEN '51000000-0000-0000-0000-000000000102'::uuid
        ELSE '51000000-0000-0000-0000-000000000201'::uuid
    END,
    'Room',
    room.id,
    'inventory.room.created.v1',
    CASE room.id
        WHEN '50000000-0000-0000-0000-000000000101'::uuid
            THEN '51000000-0000-0000-0000-000000000111'::uuid
        WHEN '50000000-0000-0000-0000-000000000102'::uuid
            THEN '51000000-0000-0000-0000-000000000112'::uuid
        ELSE '51000000-0000-0000-0000-000000000211'::uuid
    END,
    1,
    jsonb_build_object(
        'roomId', room.id,
        'roomNumber', room.room_number,
        'floorId', room.floor_id,
        'roomTypeId', room.room_type_id,
        'status', room.operational_status,
        'active', room.active,
        'currentStayId', room.current_stay_id,
        'version', room.version
    ),
    now() + interval '1 millisecond',
    NULL, 0, NULL, 0
FROM rooms room
WHERE room.id IN (
    '50000000-0000-0000-0000-000000000101',
    '50000000-0000-0000-0000-000000000102',
    '50000000-0000-0000-0000-000000000201'
)
ON CONFLICT (id) DO NOTHING;
