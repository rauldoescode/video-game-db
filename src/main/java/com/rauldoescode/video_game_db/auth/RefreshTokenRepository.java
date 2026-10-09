package com.rauldoescode.video_game_db.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(UUID familyId);

    /**
     * Row lock so two presentations of the same token run one after the other.
     * The second sees the first's revocation instead of both issuing a successor.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Marks every still-live token in the family revoked. Already-revoked rows keep
     * their original {@code revokedAt}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
            set t.revokedAt = :revokedAt
            where t.familyId = :familyId
              and t.revokedAt is null
            """)
    int revokeUnrevokedInFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
