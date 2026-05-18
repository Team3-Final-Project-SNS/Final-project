package com.example.team3final;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class Team3FinalApplication {

    public static void main(String[] args) {
        SpringApplication.run(Team3FinalApplication.class, args);
    }

}
