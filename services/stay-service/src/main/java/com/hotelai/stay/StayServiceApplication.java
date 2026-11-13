package com.hotelai.stay;

import com.hotelai.stay.config.StayProperties;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StayProperties.class)
public class StayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StayServiceApplication.class, args);
    }

    @Bean
    Clock hotelClock(StayProperties properties) {
        return Clock.system(ZoneId.of(properties.stay().timeZone()));
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
