package com.microservices.Monolith_example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MonolithExampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(MonolithExampleApplication.class, args);
	}

}
