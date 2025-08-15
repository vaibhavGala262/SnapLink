package com.vaibhavgala.url_shortner;
import io.github.cdimascio.dotenv.Dotenv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortnerApplication {

	public static void main(String[] args) {

		Dotenv dotenv = Dotenv.load();

		SpringApplication.run(UrlShortnerApplication.class, args);
	}

}
