package me.kiporenko.warframefissureschecker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WarframeFissuresCheckerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WarframeFissuresCheckerApplication.class, args);
	}

}
