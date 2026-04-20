-- =====================================================================
-- V5 — AI Superpowers: multi-agent review, generative loop, RAG template hints
-- =====================================================================
-- See migration lisab kolm uut tabelit AI-põhiste featuuride toetamiseks:
--   1. ai_reviews            — iga multi-agent council'i jooks salvestatakse
--                              (sh iga agendi score + soovitused), et ajaloost
--                              näidata kasutajale "millised fixid aitasid"
--   2. prompt_history        — RAG-lite korpus: kasutaja eestikeelne prompt +
--                              resolveeritud template + edukuse märge. Kasutatakse
--                              TemplateRagService'is similarity otsinguks
--                              (PostgreSQL full-text + trigram).
--   3. design_iterations     — generative design loop'i (iterate-until-perfect)
--                              iga sammu snapshot: score, patch, spec. Lubab
--                              frontendil näidata "evolution timeline".
-- =====================================================================

-- ---------- 1. ai_reviews --------------------------------------------
CREATE TABLE ai_reviews (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,
    design_id        BIGINT REFERENCES designs(id) ON DELETE SET NULL,

    -- Originaal intent + resolveeritud spec (JSON-ina, et mitte kaotada
    -- konteksti kui template skeem hiljem muutub).
    prompt_et        TEXT,
    spec             JSONB NOT NULL,

    -- Single-agent verdict (nagu DesignController /review täna tagastab).
    score            INT,
    verdict_et       TEXT,

    -- Multi-agent nõukogu koondvastus. JSONB võimaldab kiiret filtreerimist
    -- (nt "leia disainid kus structural < 5") ja ei nõua iga agendi kohta
    -- eraldi tabelit.
    council          JSONB,            -- { structural:{...}, print:{...}, cost:{...}, aesthetics:{...}, synthesis:{...} }
    council_score    NUMERIC(4,2),     -- kaalutud koondhinne 1.00 - 10.00

    -- Kas review sünnitas edasise iteratsiooni (generative loop).
    iteration_run_id BIGINT,

    -- Audit
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tokens_in        INT,
    tokens_out       INT
);

CREATE INDEX idx_ai_reviews_user_created ON ai_reviews(user_id, created_at DESC);
CREATE INDEX idx_ai_reviews_design      ON ai_reviews(design_id);
CREATE INDEX idx_ai_reviews_score       ON ai_reviews(council_score DESC);

-- ---------- 2. prompt_history (RAG korpus) ---------------------------
CREATE TABLE prompt_history (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,

    prompt_et        TEXT NOT NULL,
    template         VARCHAR(64) NOT NULL,
    params           JSONB NOT NULL,

    -- Edukuse indikaator: kas kasutaja tegelikult laadis STL-i alla
    -- (= lõi faili) või ainult sai spec'i. Generoorses RAG-is anname
    -- edukatele kaalu 2× kõrgemaks.
    downloaded       BOOLEAN NOT NULL DEFAULT FALSE,
    review_score     INT,                 -- viimane review hinne (kui olemas)

    -- Keele-agnostilist tokenize'imist jätame tulevikuks; praegu lihtsalt
    -- eesti keelega, mida Postgres `simple` ei lähtesta, aga pg_trgm on olemas.
    tsv              tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(prompt_et,''))) STORED,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prompt_history_tsv     ON prompt_history USING GIN (tsv);
CREATE INDEX idx_prompt_history_template ON prompt_history(template);
CREATE INDEX idx_prompt_history_created ON prompt_history(created_at DESC);

-- pg_trgm annab meile `similarity()` funktsiooni lühikese eestikeelse
-- prompti trigram-põhiseks match'imiseks. Kui extension pole olemas
-- (vanad Postgres installid), ignoreerime vea — TemplateRagService
-- langeb tagasi ainult ts_vector-otsingule.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN insufficient_privilege OR feature_not_supported THEN
    RAISE NOTICE 'pg_trgm pole saadaval — RAG kasutab ainult tsvector-otsingut';
END$$;

CREATE INDEX IF NOT EXISTS idx_prompt_history_trgm
    ON prompt_history USING GIN (prompt_et gin_trgm_ops);

-- ---------- 3. design_iterations (generative loop ajalugu) -----------
CREATE TABLE design_iterations (
    id               BIGSERIAL PRIMARY KEY,
    run_id           BIGINT NOT NULL,     -- kokku kuuluvate sammude ühendaja
    user_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,

    step             INT NOT NULL,        -- 0 = algus, kasvab
    template         VARCHAR(64) NOT NULL,
    params_before    JSONB NOT NULL,
    params_after     JSONB NOT NULL,
    patch_applied    JSONB,               -- { param: ..., new_value: ..., reason_et: ... }

    score_before     NUMERIC(4,2),
    score_after      NUMERIC(4,2),
    stopped_reason   VARCHAR(32),         -- null kui loop veel jookseb; "target_reached" / "max_iter" / "no_improvement"

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iterations_run          ON design_iterations(run_id, step);
CREATE INDEX idx_iterations_user_created ON design_iterations(user_id, created_at DESC);

-- Sequence generative-loop run_id jaoks (lihtne monotoonne counter, mitte UUID)
CREATE SEQUENCE IF NOT EXISTS design_iteration_run_seq START 1;
