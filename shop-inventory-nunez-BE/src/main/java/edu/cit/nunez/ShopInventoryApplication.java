package edu.cit.nunez;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShopInventoryApplication {
	public static void main(String[] args) {
		SpringApplication.run(ShopInventoryApplication.class, args);
	}
}