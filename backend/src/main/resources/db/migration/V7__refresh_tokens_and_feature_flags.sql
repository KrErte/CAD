-- ─────────────────────────────────────────────────────────────────────
-- V7: Refresh token rotation + feature flags + audit_log immutability
-- ─────────────────────────────────────────────────────────────────────

-- ── Refresh tokens ──────────────────────────────────────────────────
-- Short-lived access JWT (15 min) + long-lived refresh token (30 päeva).
-- Refresh token on salvestatud hash'itud (bcrypt-style) DB'sse, et DB leak
-- ei annaks ründajale kasutatavat tokenit.
CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL UNIQUE,  -- sha256(raw token) hex
    family_id       UUID NOT NULL,                 -- token lineage (detect reuse)
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  BIGINT REFERENCES refresh_tokens(id),
    client_ip       INET,
    user_agent      TEXT
);

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id, expires_at);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_hash ON refresh_tokens(token_hash);

COMMENT ON COLUMN refresh_tokens.family_id IS
  'Kui mõni token sellest family''st reuse''itakse, revoke kogu perekond (detect theft)';

-- ── Feature flags ───────────────────────────────────────────────────
-- Lihtne rollout kontroll. Kui me plaanime uue funktsiooni, saame
-- flag'i all release'ida ja seejärel järk-järgult rulluda: 10% → 50% → 100%.
CREATE TABLE feature_flags (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_percent INTEGER NOT NULL DEFAULT 0,    -- 0-100, per-user hash mod
    user_overrides  JSONB DEFAULT '[]'::jsonb,      -- ["user_id1", "user_id2"] always ON
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT rollout_range CHECK (rollout_percent BETWEEN 0 AND 100)
);

INSERT INTO feature_flags (name, description, enabled, rollout_percent) VALUES
  ('semantic_cache',          'RAG-lite pgvector cache Claude-kulu vähendamiseks', TRUE,  100),
  ('evolve_3d_preview',       'AI Superpowers evolve preview UI',                  TRUE,  50),
  ('collab_realtime',         'Collaborative design editing (Yjs)',                FALSE, 0),
  ('printflow_autopilot',     'AI otsustab automaatselt parima partneri',          FALSE, 10),
  ('meshy_3d_generation',     'Meshy.ai 3D model generation from text',            TRUE,  100);

-- ── audit_log immutability (DB-level guard) ─────────────────────────
-- Isegi kui rakendus kogemata prooviks audit-rida muuta, DB ütleb EI.
CREATE OR REPLACE FUNCTION prevent_audit_log_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is immutable — UPDATE/DELETE not permitted';
END;
$$;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

-- DELETE tohime teha ainult batch-cleanup job'ist (retention), nii et me
-- loome seal superuser-sessiooni. Tavalise app-role'ile blokkerime:
REVOKE DELETE ON audit_log FROM PUBLIC;

-- ── Stripe refunds + disputes ───────────────────────────────────────
CREATE TABLE stripe_refunds (
    id                  BIGSERIAL PRIMARY KEY,
    stripe_refund_id    VARCHAR(64) NOT NULL UNIQUE,
    stripe_payment_id   VARCHAR(64) NOT NULL,
    user_id             BIGINT REFERENCES users(id),
    amount_cents        BIGINT NOT NULL,
    currency            VARCHAR(8) NOT NULL,
    reason              VARCHAR(64),
    status              VARCHAR(32) NOT NULL,  -- pending, succeeded, failed
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_user ON stripe_refunds(user_id);
CREATE INDEX idx_refunds_payment ON stripe_refunds(stripe_payment_id);

CREATE TABLE stripe_disputes (
    id                  BIGSERIAL PRIMARY KEY,
    stripe_dispute_id   VARCHAR(64) NOT NULL UNIQUE,
    stripe_charge_id    VARCHAR(64) NOT NULL,
    user_id             BIGINT REFERENCES users(id),
    amount_cents        BIGINT NOT NULL,
    currency            VARCHAR(8) NOT NULL,
    reason              VARCHAR(64),
    status              VARCHAR(32) NOT NULL,  -- warning_needs_response, won, lost, ...
    evidence_due_by     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
