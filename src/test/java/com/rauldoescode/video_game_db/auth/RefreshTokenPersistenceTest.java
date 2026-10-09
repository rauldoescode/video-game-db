package com.rauldoescode.video_game_db.auth;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenPersistenceTest {

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    RefreshTokenRepository tokens;

    @Test
    void persistAssignsIdAndCreatedAt() {
        User user = savedUser();
        RefreshToken token = newToken(user, "a".repeat(64));

        RefreshToken saved = entityManager.persistFlushFind(token);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getRevokedAt());
        assertEquals(Instant.parse("2026-11-05T00:00:00Z"), saved.getExpiresAt());
    }

    @Test
    void deletingTheUserDeletesTheToken() {
        User user = savedUser();
        RefreshToken saved = entityManager.persistFlushFind(newToken(user, "b".repeat(64)));
        UUID familyId = saved.getFamilyId();
        UUID userId = user.getId();

        // Drop the managed token first. entityManager.remove(user) would otherwise flush
        // a RefreshToken that still points at the user Hibernate has just marked deleted.
        // The DELETE itself is what ON DELETE CASCADE has to honor.
        entityManager.clear();
        entityManager.getEntityManager()
                .createQuery("delete from User u where u.id = :id")
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.clear();

        assertTrue(tokens.findByFamilyId(familyId).isEmpty());
    }

    private User savedUser() {
        User user = new User();
        user.setUsername("raul");
        user.setEmail("raul@example.com");
        user.setPasswordHash("not-a-real-hash");
        return entityManager.persistFlushFind(user);
    }

    private static RefreshToken newToken(User user, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setFamilyId(UUID.randomUUID());
        token.setExpiresAt(Instant.parse("2026-11-05T00:00:00Z"));
        return token;
    }
}
