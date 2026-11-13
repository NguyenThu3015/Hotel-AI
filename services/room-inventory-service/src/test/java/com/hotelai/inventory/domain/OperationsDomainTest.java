package com.hotelai.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsDomainTest {
    @Test
    void housekeepingRequiresValidTransitionAndKeepsAssignment() {
        HousekeepingTask task = new HousekeepingTask(
                mock(Room.class), "HIGH", "Dọn phòng ưu tiên");
        UUID assignee = UUID.randomUUID();

        task.assign(assignee);
        task.changeStatus("IN_PROGRESS");
        task.changeStatus("COMPLETED");

        assertThat(task.getAssignedTo()).isEqualTo(assignee);
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        assertThat(task.getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> task.changeStatus("IN_PROGRESS"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void maintenanceStoresTriageAndResolutionMetadata() {
        MaintenanceBlock block = new MaintenanceBlock(
                mock(Room.class), LocalDate.now(), "Điều hòa hỏng");
        UUID actor = UUID.randomUUID();

        block.describe("Điều hòa hỏng", "Không làm lạnh", "HIGH", actor);
        block.recordResolution("Đã thay tụ điện và chạy thử");
        block.resolve(LocalDate.now());

        assertThat(block.getTitle()).isEqualTo("Điều hòa hỏng");
        assertThat(block.getSeverity()).isEqualTo("HIGH");
        assertThat(block.getCreatedBy()).isEqualTo(actor);
        assertThat(block.getResolution()).contains("chạy thử");
        assertThat(block.getStatus()).isEqualTo("RESOLVED");
    }
}
