package com.proximity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProximityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProximityApplication.class, args);
    }
}
