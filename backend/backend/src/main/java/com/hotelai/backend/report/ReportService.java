package com.hotelai.backend.report;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {
  private final JdbcTemplate jdbc;

  public Dashboard dashboard(Long hotelId, LocalDate from, LocalDate to) {
    long bookings =
        value(
            "select count(*) from bookings where hotel_id=? and created_at::date between ? and ?",
            Long.class,
            hotelId,
            Date.valueOf(from),
            Date.valueOf(to));
    long cancelled =
        value(
            "select count(*) from bookings where hotel_id=? and status='CANCELLED' and created_at::date between ? and ?",
            Long.class,
            hotelId,
            Date.valueOf(from),
            Date.valueOf(to));
    BigDecimal revenue =
        nvl(
            value(
                "select coalesce(sum(p.amount),0) from payments p join bookings b on b.id=p.booking_id where b.hotel_id=? and p.status='SUCCEEDED' and p.paid_at::date between ? and ?",
                BigDecimal.class,
                hotelId,
                Date.valueOf(from),
                Date.valueOf(to)));
    long sold =
        value(
            "select count(*) from booking_rooms br join bookings b on b.id=br.booking_id where b.hotel_id=? and b.status in ('CONFIRMED','CHECKED_IN','CHECKED_OUT') and b.check_in_date < ? and b.check_out_date > ?",
            Long.class,
            hotelId,
            Date.valueOf(to.plusDays(1)),
            Date.valueOf(from));
    long roomCount =
        value(
            "select count(*) from rooms where hotel_id=? and status<>'MAINTENANCE'",
            Long.class,
            hotelId);
    long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(from, to.plusDays(1)));
    long capacity = roomCount * days;
    BigDecimal occupancy =
        capacity == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(sold * 100.0 / capacity).setScale(2, RoundingMode.HALF_UP);
    BigDecimal adr =
        sold == 0
            ? BigDecimal.ZERO
            : revenue.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP);
    BigDecimal revpar =
        capacity == 0
            ? BigDecimal.ZERO
            : revenue.divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
    BigDecimal cancelRate =
        bookings == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(cancelled * 100.0 / bookings).setScale(2, RoundingMode.HALF_UP);
    return new Dashboard(
        revenue,
        bookings,
        cancelRate,
        occupancy,
        adr,
        revpar,
        daily(hotelId, from, to),
        popularServices(hotelId, from, to),
        popularRoomTypes(hotelId, from, to),
        bookingSources(hotelId, from, to));
  }

  public List<Map<String, Object>> daily(Long h, LocalDate f, LocalDate t) {
    return jdbc.queryForList(
        "select p.paid_at::date as day, sum(p.amount) as revenue, count(distinct b.id) as bookings from payments p join bookings b on b.id=p.booking_id where b.hotel_id=? and p.status='SUCCEEDED' and p.paid_at::date between ? and ? group by p.paid_at::date order by day",
        h,
        Date.valueOf(f),
        Date.valueOf(t));
  }

  public List<Map<String, Object>> popularServices(Long h, LocalDate f, LocalDate t) {
    return jdbc.queryForList(
        "select s.name, sum(u.quantity) as quantity, sum(u.total_amount) as revenue from service_usages u join hotel_services s on s.id=u.service_id where s.hotel_id=? and u.used_at::date between ? and ? group by s.id,s.name order by revenue desc limit 10",
        h,
        Date.valueOf(f),
        Date.valueOf(t));
  }

  public List<Map<String, Object>> popularRoomTypes(Long h, LocalDate f, LocalDate t) {
    return jdbc.queryForList(
        "select rt.name, count(br.id) as bookings, sum(br.nightly_price * (b.check_out_date-b.check_in_date)) as revenue from booking_rooms br join bookings b on b.id=br.booking_id join room_types rt on rt.id=br.room_type_id where b.hotel_id=? and b.created_at::date between ? and ? group by rt.id,rt.name order by bookings desc",
        h,
        Date.valueOf(f),
        Date.valueOf(t));
  }

  public List<Map<String, Object>> bookingSources(Long h, LocalDate f, LocalDate t) {
    return jdbc.queryForList(
        "select source, count(*) as bookings from bookings where hotel_id=? and created_at::date between ? and ? group by source order by bookings desc",
        h,
        Date.valueOf(f),
        Date.valueOf(t));
  }

  public byte[] excel(Dashboard d) throws Exception {
    try (var wb = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = wb.createSheet("Dashboard");
      int i = 0;
      for (var e : metrics(d).entrySet()) {
        Row row = sheet.createRow(i++);
        row.createCell(0).setCellValue(e.getKey());
        row.createCell(1).setCellValue(e.getValue());
      }
      sheet.autoSizeColumn(0);
      sheet.autoSizeColumn(1);
      wb.write(out);
      return out.toByteArray();
    }
  }

  public byte[] pdf(Dashboard d) throws Exception {
    try (var out = new ByteArrayOutputStream()) {
      Document doc = new Document();
      PdfWriter.getInstance(doc, out);
      doc.open();
      doc.add(new Paragraph("HOTEL MANAGEMENT REPORT"));
      PdfPTable table = new PdfPTable(2);
      for (var e : metrics(d).entrySet()) {
        table.addCell(e.getKey());
        table.addCell(e.getValue());
      }
      doc.add(table);
      doc.close();
      return out.toByteArray();
    }
  }

  private Map<String, String> metrics(Dashboard d) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("Revenue", d.revenue.toPlainString());
    m.put("Bookings", String.valueOf(d.bookings));
    m.put("Cancellation rate (%)", d.cancellationRate.toPlainString());
    m.put("Occupancy (%)", d.occupancyRate.toPlainString());
    m.put("ADR", d.adr.toPlainString());
    m.put("RevPAR", d.revpar.toPlainString());
    return m;
  }

  private <T> T value(String sql, Class<T> type, Object... args) {
    return jdbc.queryForObject(sql, type, args);
  }

  private BigDecimal nvl(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  public record Dashboard(
      BigDecimal revenue,
      long bookings,
      BigDecimal cancellationRate,
      BigDecimal occupancyRate,
      BigDecimal adr,
      BigDecimal revpar,
      List<Map<String, Object>> dailyRevenue,
      List<Map<String, Object>> popularServices,
      List<Map<String, Object>> popularRoomTypes,
      List<Map<String, Object>> bookingSources) {}
}
