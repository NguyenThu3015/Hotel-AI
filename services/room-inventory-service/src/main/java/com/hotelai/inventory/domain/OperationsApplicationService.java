package com.hotelai.inventory.domain;

import static com.hotelai.inventory.api.InventoryDtos.*;

import com.hotelai.inventory.common.ApiException;
import com.hotelai.inventory.common.RequestContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsApplicationService {
    private static final List<String> ACTIVE_TASK_STATUSES = List.of("OPEN", "IN_PROGRESS");

    private final HotelProfileRepository hotels;
    private final RoomRepository rooms;
    private final HousekeepingTaskRepository housekeepingTasks;
    private final MaintenanceBlockRepository maintenanceBlocks;
    private final CatalogApplicationService catalog;

    public OperationsApplicationService(
            HotelProfileRepository hotels,
            RoomRepository rooms,
            HousekeepingTaskRepository housekeepingTasks,
            MaintenanceBlockRepository maintenanceBlocks,
            CatalogApplicationService catalog) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.housekeepingTasks = housekeepingTasks;
        this.maintenanceBlocks = maintenanceBlocks;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<HotelProfileView> publicHotels() {
        return hotels.findAll().stream()
                .filter(HotelProfile::isActive)
                .map(OperationsApplicationService::hotelView)
                .toList();
    }

    @Transactional
    public HotelProfileView updateHotel(short id, HotelProfileUpdateRequest request) {
        if (id != 1) throw ApiException.notFound("Khách sạn không tồn tại.");
        HotelProfile hotel = hotels.findById(id)
                .orElseThrow(() -> ApiException.notFound("Khách sạn không tồn tại."));
        verifyVersion(hotel.getVersion(), request.expectedVersion(), "hotel-profile-version");
        hotel.update(
                request.name(),
                request.address(),
                request.description(),
                request.phone(),
                request.email(),
                request.active());
        return hotelView(hotel);
    }

    @Transactional
    public List<HousekeepingTaskView> housekeepingTasks() {
        for (Room room : rooms.findByOperationalStatusOrderByRoomNumberAsc(
                RoomStatus.WAITING_CLEANING)) {
            if (!housekeepingTasks.existsByRoomIdAndStatusIn(
                    room.getId(), ACTIVE_TASK_STATUSES)) {
                housekeepingTasks.save(new HousekeepingTask(
                        room, "MEDIUM", "Dọn và kiểm tra phòng sau check-out"));
            }
        }
        return housekeepingTasks.findAllByOrderByCreatedAtDesc().stream()
                .map(OperationsApplicationService::housekeepingView)
                .toList();
    }

    @Transactional
    public HousekeepingTaskView assign(
            UUID taskId, HousekeepingAssignmentRequest request) {
        HousekeepingTask task = requiredTask(taskId);
        verifyVersion(task.getVersion(), request.expectedVersion(), "housekeeping-version");
        task.assign(request.assignedTo());
        return housekeepingView(task);
    }

    @Transactional
    public HousekeepingTaskView changeTaskStatus(
            UUID taskId, HousekeepingStatusRequest request) {
        HousekeepingTask task = requiredTask(taskId);
        verifyVersion(task.getVersion(), request.expectedVersion(), "housekeeping-version");
        task.changeStatus(request.status());
        if ("COMPLETED".equals(request.status())) {
            Room room = task.getRoom();
            catalog.changeStatus(
                    room.getId(),
                    new RoomStatusChangeRequest(
                            RoomStatus.AVAILABLE,
                            "Housekeeping đã hoàn tất và xác nhận phòng sẵn sàng",
                            room.getVersion()),
                    "housekeeping",
                    RequestContext.actorId());
        }
        return housekeepingView(task);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceView> maintenance() {
        return maintenanceBlocks.findByStatusOrderByCreatedAtDesc("OPEN").stream()
                .map(OperationsApplicationService::maintenanceView)
                .toList();
    }

    @Transactional
    public MaintenanceView createMaintenance(MaintenanceCreateRequest request) {
        Room room = rooms.findForStatusChange(request.roomId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng."));
        if (room.getOperationalStatus() != RoomStatus.MAINTENANCE) {
            catalog.changeStatus(
                    room.getId(),
                    new RoomStatusChangeRequest(
                            RoomStatus.MAINTENANCE,
                            request.title(),
                            room.getVersion()),
                    "maintenance",
                    RequestContext.actorId());
        }
        MaintenanceBlock block = maintenanceBlocks
                .findFirstByRoomIdAndStatusOrderByCreatedAtDesc(room.getId(), "OPEN")
                .orElseThrow(() -> new IllegalStateException("Maintenance block was not created"));
        block.describe(
                request.title(),
                request.description(),
                request.severity(),
                RequestContext.actorId());
        return maintenanceView(block);
    }

    @Transactional
    public MaintenanceView resolveMaintenance(
            UUID maintenanceId, MaintenanceResolveRequest request) {
        MaintenanceBlock block = maintenanceBlocks.findById(maintenanceId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu bảo trì."));
        verifyVersion(block.getVersion(), request.expectedVersion(), "maintenance-version");
        if (!"OPEN".equals(block.getStatus())) {
            throw ApiException.conflict("maintenance-closed", "Yêu cầu bảo trì đã kết thúc.");
        }
        Room room = block.getRoom();
        catalog.changeStatus(
                room.getId(),
                new RoomStatusChangeRequest(
                        RoomStatus.AVAILABLE,
                        "Bảo trì hoàn tất: " + request.note(),
                        room.getVersion()),
                "maintenance",
                RequestContext.actorId());
        block.recordResolution(request.note());
        return maintenanceView(block);
    }

    private HousekeepingTask requiredTask(UUID taskId) {
        return housekeepingTasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy công việc buồng phòng."));
    }

    private static void verifyVersion(long current, long expected, String code) {
        if (current != expected) {
            throw ApiException.conflict(
                    code, "Dữ liệu đã được người khác cập nhật; hãy tải lại trước khi thao tác.");
        }
    }

    private static HotelProfileView hotelView(HotelProfile value) {
        return new HotelProfileView(
                value.getId(),
                value.getName(),
                value.getAddress(),
                value.getDescription(),
                value.getPhone(),
                value.getEmail(),
                value.isActive(),
                value.getVersion());
    }

    private static HousekeepingTaskView housekeepingView(HousekeepingTask value) {
        return new HousekeepingTaskView(
                value.getId(),
                value.getRoom().getId(),
                value.getRoom().getRoomNumber(),
                value.getAssignedTo(),
                value.getStatus(),
                value.getPriority(),
                value.getNote(),
                value.getCreatedAt(),
                value.getUpdatedAt(),
                value.getCompletedAt(),
                value.getVersion());
    }

    private static MaintenanceView maintenanceView(MaintenanceBlock value) {
        return new MaintenanceView(
                value.getId(),
                value.getRoom().getId(),
                value.getRoom().getRoomNumber(),
                value.getTitle(),
                value.getDescription(),
                value.getSeverity(),
                value.getStatus(),
                value.getResolution(),
                value.getCreatedBy(),
                value.getStartDate(),
                value.getEndDate(),
                value.getCreatedAt(),
                value.getResolvedAt(),
                value.getVersion());
    }
}
