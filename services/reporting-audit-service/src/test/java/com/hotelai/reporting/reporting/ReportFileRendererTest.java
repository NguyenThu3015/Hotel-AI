package com.hotelai.reporting.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.reporting.reporting.ReportFileRenderer.ExportFormat;
import com.hotelai.reporting.reporting.ReportingQueryService.Dashboard;
import com.hotelai.reporting.reporting.ReportingQueryService.PeriodComparison;
import com.hotelai.reporting.reporting.ReportingQueryService.ReportContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReportFileRendererTest {
    private final ReportFileRenderer renderer = new ReportFileRenderer();

    @ParameterizedTest
    @EnumSource(ExportFormat.class)
    void createsNonEmptyReportForEverySupportedFormat(ExportFormat format) {
        var result = renderer.render(dashboard(), format);

        assertThat(result.content()).isNotEmpty();
        assertThat(result.contentType()).isNotBlank();
        assertThat(result.fileName()).endsWith("." + format.name().toLowerCase());
    }

    private static Dashboard dashboard() {
        return new Dashboard(
                new BigDecimal("1200000"),
                new BigDecimal("150000"),
                10,
                new BigDecimal("10.00"),
                new BigDecimal("75.00"),
                new BigDecimal("800000"),
                new BigDecimal("600000"),
                List.of(Map.of("day", "2026-07-17", "revenue", 1200000)),
                List.of(),
                List.of(),
                List.of(),
                new PeriodComparison(
                        BigDecimal.ZERO,
                        0,
                        BigDecimal.ZERO,
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00")),
                new ReportContext(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        "Asia/Ho_Chi_Minh",
                        "VND",
                        null,
                        null,
                        Instant.now()),
                true,
                null,
                Map.of());
    }
}
