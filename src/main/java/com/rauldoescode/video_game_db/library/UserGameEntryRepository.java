package com.rauldoescode.video_game_db.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserGameEntryRepository extends JpaRepository<UserGameEntry, UUID> {

    /**
     * Newest change first. {@code join fetch} loads each game in the same query, so mapping the
     * response does not touch a lazy proxy after the transaction has closed.
     */
    @Query("""
            select e from UserGameEntry e
            join fetch e.game
            where e.user.id = :userId
            order by e.updatedAt desc
            """)
    List<UserGameEntry> findLibrary(@Param("userId") UUID userId);

    /**
     * Same as {@link #findLibrary}, limited to one status.
     */
    @Query("""
            select e from UserGameEntry e
            join fetch e.game
            where e.user.id = :userId
              and e.status = :status
            order by e.updatedAt desc
            """)
    List<UserGameEntry> findLibraryByStatus(@Param("userId") UUID userId, @Param("status") GameStatus status);
}
