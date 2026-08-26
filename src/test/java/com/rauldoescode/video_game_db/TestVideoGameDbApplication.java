package com.rauldoescode.video_game_db;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;

public class TestVideoGameDbApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.from(VideoGameDbApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
