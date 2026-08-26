package com.rauldoescode.video_game_db;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VideoGameDbApplicationTests {

	@Test
	void contextLoads() {
	}

}
