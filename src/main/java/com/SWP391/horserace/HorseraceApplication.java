package com.SWP391.horserace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HorseraceApplication {

	public static void main(String[] args) {
		SpringApplication.run(HorseraceApplication.class, args);
	}

}
