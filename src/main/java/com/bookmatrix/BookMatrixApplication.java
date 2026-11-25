package com.bookmatrix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point: starts the embedded Tomcat server on http://localhost:8080 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling       // turns on HoldExpiryJob
public class BookMatrixApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookMatrixApplication.class, args);
    }
}
