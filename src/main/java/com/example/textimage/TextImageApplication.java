package com.example.textimage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TextImageApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TextImageApplication.class);
        application.setHeadless(true);
        application.run(args);
    }
}
