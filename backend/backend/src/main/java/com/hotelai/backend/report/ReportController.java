package com.hotelai.backend.report;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ReportController {
  private final ReportService service;

  @GetMapping("/dashboard")
  ReportService.Dashboard dashboard(
      @RequestParam Long hotelId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.dashboard(hotelId, from, to);
  }

  @GetMapping("/export.xlsx")
  ResponseEntity<byte[]> excel(
      @RequestParam Long hotelId, @RequestParam LocalDate from, @RequestParam LocalDate to)
      throws Exception {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hotel-report.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(service.excel(service.dashboard(hotelId, from, to)));
  }

  @GetMapping("/export.pdf")
  ResponseEntity<byte[]> pdf(
      @RequestParam Long hotelId, @RequestParam LocalDate from, @RequestParam LocalDate to)
      throws Exception {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=hotel-report.pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(service.pdf(service.dashboard(hotelId, from, to)));
  }
}
