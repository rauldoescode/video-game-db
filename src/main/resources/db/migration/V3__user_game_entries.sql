CREATE TABLE user_game_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id BIGINT NOT NULL REFERENCES games(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PLAN_TO_PLAY',
    rating INT,
    review TEXT,
    hours_played INT NOT NULL DEFAULT 0,
    platform_played VARCHAR(50),
    started_at DATE,
    completed_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_game UNIQUE (user_id, game_id),
    CONSTRAINT chk_status CHECK (status IN (
        'PLAN_TO_PLAY', 'PLAYING', 'COMPLETED', 'ON_HOLD', 'DROPPED'
    )),
    CONSTRAINT chk_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 10)),
    CONSTRAINT chk_hours CHECK (hours_played >= 0)
);

-- GET /api/library sorts by updated_at desc within one user.
CREATE INDEX idx_user_game_entries_user_updated ON user_game_entries (user_id, updated_at DESC);
