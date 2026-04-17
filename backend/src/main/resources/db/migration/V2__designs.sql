CREATE TABLE designs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template    VARCHAR(64) NOT NULL,
    params      JSONB NOT NULL,
    summary_et  VARCHAR(255),
    stl         BYTEA NOT NULL,
    size_bytes  INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_designs_user_created ON designs(user_id, created_at DESC);
