package com.bighealth;

import com.bighealth.config.KgProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KgProperties.class)
public class BigHealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BigHealthApplication.class, args);
    }
}