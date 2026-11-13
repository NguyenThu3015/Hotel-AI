package com.hotelai.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RoomInventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoomInventoryServiceApplication.class, args);
    }
}
