package com.hotelai.servicecatalog;

import com.hotelai.servicecatalog.config.CatalogProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CatalogProperties.class)
public class HotelServiceCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(HotelServiceCatalogApplication.class, args);
    }

    @Bean
    Clock hotelClock(CatalogProperties properties) {
        return Clock.system(ZoneId.of(properties.timeZone()));
    }
}
