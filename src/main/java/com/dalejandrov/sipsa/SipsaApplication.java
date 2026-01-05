package com.dalejandrov.sipsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SipsaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SipsaApplication.class, args);
	}

}
