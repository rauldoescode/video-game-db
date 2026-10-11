package com.rauldoescode.video_game_db.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
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

    /**
     * One entry, only if it belongs to this user. Ownership is part of the query, so another
     * user's id and a missing id are the same empty result.
     */
    @Query("""
            select e from UserGameEntry e
            join fetch e.game
            where e.id = :entryId
              and e.user.id = :userId
            """)
    Optional<UserGameEntry> findOwned(@Param("entryId") UUID entryId, @Param("userId") UUID userId);

    /**
     * Deletes one entry if it belongs to this user.
     *
     * @return rows deleted, 0 when the id is missing or someone else's
     */
    @Modifying
    @Query("delete from UserGameEntry e where e.id = :entryId and e.user.id = :userId")
    int deleteOwned(@Param("entryId") UUID entryId, @Param("userId") UUID userId);
}
