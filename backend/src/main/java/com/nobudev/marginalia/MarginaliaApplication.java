package com.nobudev.marginalia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarginaliaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarginaliaApplication.class, args);
    }
}
