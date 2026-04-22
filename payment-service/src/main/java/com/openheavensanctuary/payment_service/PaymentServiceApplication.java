package com.openheavensanctuary.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class PaymentServiceApplication {


	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();

		System.setProperty("SPRING_DATASOURCE_URL", dotenv.get("SPRING_DATASOURCE_URL"));
		System.setProperty("SPRING_DATASOURCE_USERNAME", dotenv.get("SPRING_DATASOURCE_USERNAME"));

		System.setProperty("SPRING_DATASOURCE_PASSWORD", dotenv.get("SPRING_DATASOURCE_PASSWORD"));
		System.setProperty("PAYSTACK_SECRET_KEY", dotenv.get("PAYSTACK_SECRET_KEY"));
		System.setProperty("PAYSTACK_PUBLIC_KEY", dotenv.get("PAYSTACK_PUBLIC_KEY"));


		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
