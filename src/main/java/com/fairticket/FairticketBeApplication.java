package com.fairticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FairticketBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(FairticketBeApplication.class, args);
	}

}
