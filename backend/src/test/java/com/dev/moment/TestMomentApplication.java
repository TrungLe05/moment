package com.dev.moment;

import org.springframework.boot.SpringApplication;

public class TestMomentApplication {

	public static void main(String[] args) {
		SpringApplication.from(MomentApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
