package com.hotelai.reporting;

import com.hotelai.reporting.config.ReportingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ReportingProperties.class)
public class ReportingAuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportingAuditServiceApplication.class, args);
    }
}
