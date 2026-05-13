package com.tripplanner.external_info_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching // Wichtig, damit dein @Cacheable im ApiProxyService funktioniert!
public class ExternalInfoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExternalInfoServiceApplication.class, args);
    }
}