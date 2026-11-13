package com.hotelai.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(IdentityServiceApplication.class, args);
  }
}
