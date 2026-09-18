package com.rauldoescode.video_game_db.user;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPersistenceTest {

    @Autowired
    TestEntityManager entityManager;

    @Test
    void persistAssignsIdAndTimestamps() {
        User user = new User();
        user.setUsername("raul");
        user.setEmail("raul@example.com");
        user.setPasswordHash("not-a-real-hash");

        User saved = entityManager.persistFlushFind(user);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertTrue(saved.isProfilePublic());
        assertEquals("raul", saved.getUsername());

        UUID id = saved.getId();
        Instant createdAt = saved.getCreatedAt();

        saved.setBio("hello");
        User updated = entityManager.persistFlushFind(saved);

        assertEquals(id, updated.getId());
        assertEquals(createdAt, updated.getCreatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(createdAt));
    }
}
