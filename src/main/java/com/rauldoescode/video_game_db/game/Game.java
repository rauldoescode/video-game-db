package com.rauldoescode.video_game_db.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a game in the database.
 */
@Entity
@Table(name = "games")
@Getter
@Setter
public class Game {

    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(unique = true)
    private String slug;

    @Column(length = 64)
    private String coverImageId;

    private LocalDate releaseDate;

    @Column(precision = 5, scale = 2)
    private BigDecimal aggregatedRating;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant cachedAt;
}
