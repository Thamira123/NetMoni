package com.netmoni.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NetMoniServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(NetMoniServerApplication.class, args);
	}

}
