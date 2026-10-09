package com.rauldoescode.video_game_db.library;

import com.rauldoescode.video_game_db.TestcontainersConfiguration;
import com.rauldoescode.video_game_db.game.Game;
import com.rauldoescode.video_game_db.user.User;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserGameEntryPersistenceTest {

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    UserGameEntryRepository entries;

    @Test
    void persistAppliesDefaultsAndTimestamps() {
        UserGameEntry saved = entityManager.persistFlushFind(newEntry(savedUser("raul"), savedGame(1020L)));

        assertNotNull(saved.getId());
        assertEquals(GameStatus.PLAN_TO_PLAY, saved.getStatus());
        assertEquals(0, saved.getHoursPlayed());
        assertNull(saved.getRating());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void rawInsertGetsTheSqlDefaults() {
        User user = savedUser("raul");
        savedGame(1020L);

        entityManager.getEntityManager()
                .createNativeQuery("insert into user_game_entries (user_id, game_id) values (:userId, 1020)")
                .setParameter("userId", user.getId())
                .executeUpdate();

        Object[] row = (Object[]) entityManager.getEntityManager()
                .createNativeQuery("select status, hours_played from user_game_entries")
                .getSingleResult();
        assertEquals("PLAN_TO_PLAY", row[0]);
        assertEquals(0, ((Number) row[1]).intValue());
    }

    @Test
    void statusOutsideTheFiveValuesIsRejected() {
        User user = savedUser("raul");
        savedGame(1020L);

        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("insert into user_game_entries (user_id, game_id, status) values (:userId, 1020, 'BACKLOG')")
                .setParameter("userId", user.getId())
                .executeUpdate());
    }

    @Test
    void ratingOutsideOneToTenIsRejected() {
        UserGameEntry entry = newEntry(savedUser("raul"), savedGame(1020L));
        entry.setRating(11);

        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(entry));
    }

    @Test
    void negativeHoursAreRejected() {
        UserGameEntry entry = newEntry(savedUser("raul"), savedGame(1020L));
        entry.setHoursPlayed(-1);

        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(entry));
    }

    @Test
    void sameGameTwiceForOneUserIsRejected() {
        User user = savedUser("raul");
        Game game = savedGame(1020L);
        entityManager.persistAndFlush(newEntry(user, game));

        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(newEntry(user, game)));
    }

    @Test
    void deletingTheUserDeletesTheirEntries() {
        User user = savedUser("raul");
        entityManager.persistAndFlush(newEntry(user, savedGame(1020L)));
        UUID userId = user.getId();

        // A JPQL delete, not entityManager.remove(user): the DELETE is what ON DELETE CASCADE honors.
        entityManager.clear();
        entityManager.getEntityManager()
                .createQuery("delete from User u where u.id = :id")
                .setParameter("id", userId)
                .executeUpdate();
        entityManager.clear();

        assertEquals(0, entries.count());
        // The games row stays; other users may still have it.
        assertNotNull(entityManager.find(Game.class, 1020L));
    }

    @Test
    void deletingAGameThatIsStillInALibraryIsRejected() {
        entityManager.persistAndFlush(newEntry(savedUser("raul"), savedGame(1020L)));
        entityManager.clear();

        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("delete from games where id = 1020")
                .executeUpdate());
    }

    @Test
    void libraryIsNewestChangeFirstAndFiltersByStatus() {
        User raul = savedUser("raul");
        User other = savedUser("other");
        UserGameEntry older = entityManager.persistAndFlush(newEntry(raul, savedGame(1L)));
        UserGameEntry playing = newEntry(raul, savedGame(2L));
        playing.setStatus(GameStatus.PLAYING);
        entityManager.persistAndFlush(playing);
        entityManager.persistAndFlush(newEntry(other, entityManager.find(Game.class, 1L)));

        // Touching the older entry moves it to the top.
        older.setHoursPlayed(5);
        entityManager.flush();
        entityManager.clear();

        List<UserGameEntry> library = entries.findLibrary(raul.getId());
        assertEquals(List.of(1L, 2L), library.stream().map(e -> e.getGame().getId()).toList());
        assertTrue(library.stream().allMatch(e -> e.getUser().getId().equals(raul.getId())));

        List<UserGameEntry> onlyPlaying = entries.findLibraryByStatus(raul.getId(), GameStatus.PLAYING);
        assertEquals(List.of(2L), onlyPlaying.stream().map(e -> e.getGame().getId()).toList());
    }

    private User savedUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        return entityManager.persistFlushFind(user);
    }

    private Game savedGame(long igdbId) {
        Game game = new Game();
        game.setId(igdbId);
        game.setTitle("Game " + igdbId);
        return entityManager.persistFlushFind(game);
    }

    private static UserGameEntry newEntry(User user, Game game) {
        UserGameEntry entry = new UserGameEntry();
        entry.setUser(user);
        entry.setGame(game);
        return entry;
    }
}
