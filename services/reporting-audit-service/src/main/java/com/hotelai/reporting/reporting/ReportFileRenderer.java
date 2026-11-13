package com.hotelai.reporting.reporting;

import com.hotelai.reporting.reporting.ReportingQueryService.Dashboard;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ReportFileRenderer {
    public RenderedReport render(Dashboard dashboard, ExportFormat format) {
        return switch (format) {
            case XLSX -> new RenderedReport(
                    excel(dashboard),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "hotel-report-" + dashboard.context().from() + "-"
                            + dashboard.context().to() + ".xlsx");
            case PDF -> new RenderedReport(
                    pdf(dashboard),
                    "application/pdf",
                    "hotel-report-" + dashboard.context().from() + "-"
                            + dashboard.context().to() + ".pdf");
            case CSV -> new RenderedReport(
                    csv(dashboard),
                    "text/csv;charset=UTF-8",
                    "hotel-report-" + dashboard.context().from() + "-"
                            + dashboard.context().to() + ".csv");
        };
    }

    byte[] excel(Dashboard dashboard) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Dashboard");
            CellStyle header = workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);

            Row heading = sheet.createRow(0);
            heading.createCell(0).setCellValue("Chỉ số");
            heading.createCell(1).setCellValue("Giá trị");
            heading.getCell(0).setCellStyle(header);
            heading.getCell(1).setCellStyle(header);

            int rowIndex = 1;
            for (Map.Entry<String, String> metric : metrics(dashboard).entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(metric.getKey());
                row.createCell(1).setCellValue(metric.getValue());
            }
            rowIndex++;
            Row dailyHeading = sheet.createRow(rowIndex++);
            dailyHeading.createCell(0).setCellValue("Ngày");
            dailyHeading.createCell(1).setCellValue("Doanh thu");
            dailyHeading.getCell(0).setCellStyle(header);
            dailyHeading.getCell(1).setCellStyle(header);
            for (Map<String, Object> item : dashboard.dailyRevenue()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(String.valueOf(item.get("day")));
                row.createCell(1).setCellValue(String.valueOf(item.get("revenue")));
            }
            sheet.setColumnWidth(0, 32 * 256);
            sheet.setColumnWidth(1, 24 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create XLSX report", error);
        }
    }

    byte[] pdf(Dashboard dashboard) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("HOTEL MANAGEMENT REPORT"));
            document.add(new Paragraph(
                    "Period: " + dashboard.context().from() + " - "
                            + dashboard.context().to()));
            document.add(new Paragraph(
                    "Timezone: " + dashboard.context().timeZone()
                            + " | Currency: " + dashboard.context().currency()));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.addCell("Metric");
            table.addCell("Value");
            for (Map.Entry<String, String> metric : metrics(dashboard).entrySet()) {
                table.addCell(metric.getKey());
                table.addCell(metric.getValue());
            }
            document.add(table);
            if (!dashboard.dataAvailable()) {
                document.add(new Paragraph("No data in the selected range."));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create PDF report", error);
        }
    }

    byte[] csv(Dashboard dashboard) {
        StringBuilder output = new StringBuilder("\uFEFFmetric,value\r\n");
        metrics(dashboard).forEach((key, value) -> output
                .append(csvCell(key)).append(',').append(csvCell(value)).append("\r\n"));
        output.append("\r\nday,revenue\r\n");
        dashboard.dailyRevenue().forEach(item -> output
                .append(csvCell(String.valueOf(item.get("day"))))
                .append(',')
                .append(csvCell(String.valueOf(item.get("revenue"))))
                .append("\r\n"));
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> metrics(Dashboard value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Revenue", value.revenue().toPlainString());
        result.put("Service revenue", value.serviceRevenue().toPlainString());
        result.put("Bookings", String.valueOf(value.bookings()));
        result.put("Cancellation rate (%)", value.cancellationRate().toPlainString());
        result.put("Occupancy (%)", value.occupancyRate().toPlainString());
        result.put("ADR", value.adr().toPlainString());
        result.put("RevPAR", value.revpar().toPlainString());
        return result;
    }

    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public enum ExportFormat {
        XLSX,
        PDF,
        CSV
    }

    public record RenderedReport(byte[] content, String contentType, String fileName) {}
}
