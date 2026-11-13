package com.hotelai.reporting.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.reporting.common.ApiException;
import com.hotelai.reporting.config.ReportingProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportingQueryServiceTest {
    private final ReportingQueryService service = new ReportingQueryService(
            org.mockito.Mockito.mock(JdbcTemplate.class),
            new ReportingProperties(
                    "VND",
                    "Asia/Ho_Chi_Minh",
                    "test-key",
                    366,
                    365,
                    null));

    @Test
    void inclusiveDateRangeUsesConfiguredHotelTimeZone() {
        ReportingQueryService.Range range = service.range(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(range.start().toString()).isEqualTo("2026-06-30T17:00:00Z");
        assertThat(range.end().toString()).isEqualTo("2026-07-31T17:00:00Z");
    }

    @Test
    void rejectsReversedOrExcessiveRanges() {
        assertThatThrownBy(() -> service.range(
                LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("before");

        assertThatThrownBy(() -> service.range(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("366");
    }
}
