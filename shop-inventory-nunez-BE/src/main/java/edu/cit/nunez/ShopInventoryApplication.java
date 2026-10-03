package edu.cit.nunez;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@SpringBootApplication
public class ShopInventoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShopInventoryApplication.class, args);
	}

	/**
	 * Unique instance id per application startup, as specified by Tiangge API:
	 * "Each time your application starts, it creates a new random UUID and sends
	 * it as X-Client-Instance on every call for as long as it runs."
	 */
	@Bean(name = "appInstanceId")
	public String appInstanceId(@Value("${INSTANCE_ID:}") String envInstanceId) {
		if (envInstanceId != null && !envInstanceId.isBlank()) {
			return envInstanceId.trim();
		}
		return UUID.randomUUID().toString();
	}

	@Bean(name = "appStartedAt")
	public Instant appStartedAt() {
		return Instant.now();
	}
}
