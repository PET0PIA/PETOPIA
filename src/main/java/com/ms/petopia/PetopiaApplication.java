package com.ms.petopia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PetopiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetopiaApplication.class, args);
    }

}
