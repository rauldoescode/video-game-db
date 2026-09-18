package com.rauldoescode.video_game_db.user;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    UserRepository users;

    @Test
    void findByUsernameAndFindByEmailReturnTheSameRow() {
        User user = new User();
        user.setUsername("raul");
        user.setEmail("raul@example.com");
        user.setPasswordHash("not-a-real-hash");
        users.saveAndFlush(user);

        User byUsername = users.findByUsername("raul").orElseThrow();
        User byEmail = users.findByEmail("raul@example.com").orElseThrow();

        assertEquals(byUsername.getId(), byEmail.getId());
        assertEquals("raul", byEmail.getUsername());
    }

    @Test
    void missingUsernameOrEmailIsEmpty() {
        assertTrue(users.findByUsername("nobody").isEmpty());
        assertTrue(users.findByEmail("nobody@example.com").isEmpty());
    }
}
