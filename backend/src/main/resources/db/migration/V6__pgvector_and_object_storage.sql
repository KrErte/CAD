-- ─────────────────────────────────────────────────────────────────────
-- V6: pgvector RAG-lite + S3 object storage refactor
-- ─────────────────────────────────────────────────────────────────────
-- Eesmärk:
--  1. Vabastada Postgres binaarandmetest — STL/Gcode lähevad S3'sse,
--     Postgres hoiab ainult viidet (s3_key + etag).
--  2. Lisada embeddings tabel, et RAG-lite leiaks sarnased promptid.
--     Sama prompt + sarnane stil = cached spec (säästab ~90% Claude-kulu
--     korduvate generatsioonide puhul).

-- ── pgvector extension ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS vector;

-- ── Embeddings tabel (prompt -> spec cache) ─────────────────────────
CREATE TABLE prompt_embeddings (
    id            BIGSERIAL PRIMARY KEY,
    prompt_hash   VARCHAR(64) NOT NULL,   -- sha256(normalized prompt)
    prompt_text   TEXT NOT NULL,
    -- 1536-dim = OpenAI ada-002 või Voyage embed-3, hea kompromiss kvaliteet/size
    embedding     vector(1536) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    spec_json     JSONB NOT NULL,
    hit_count     INTEGER NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_hit_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- IVFFlat index — võimaldab kNN otsingut O(√n) ajaga 100k+ rida'de puhul
-- lists = 100 on hea thumb-of-rule, kui N = 10k-1M; kohanda prod'is
CREATE INDEX idx_prompt_embeddings_vec ON prompt_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX idx_prompt_embeddings_hash ON prompt_embeddings(prompt_hash);
CREATE INDEX idx_prompt_embeddings_template ON prompt_embeddings(template_name);

-- ── S3 viited designs tabelisse ─────────────────────────────────────
-- stl_bytes BYTEA veerg on deprecated — hoiame tagasiühilduvuse pärast, aga
-- uued disainid kirjutavad ainult s3_key'd. Migratsioon V7 eemaldab bytea.
ALTER TABLE designs
    ADD COLUMN stl_s3_key VARCHAR(512),
    ADD COLUMN stl_s3_etag VARCHAR(64),
    ADD COLUMN stl_size_bytes BIGINT,
    ADD COLUMN preview_png_s3_key VARCHAR(512);

-- Printflow Gcode samamoodi — build-plate STL-id +  gcode
ALTER TABLE build_plates
    ADD COLUMN gcode_s3_key VARCHAR(512),
    ADD COLUMN gcode_size_bytes BIGINT;

-- ── Audit log tabel (Security docs TODO) ────────────────────────────
-- Iga mutation-tegevus (login, design create, order, admin action) saab
-- immutable record'i, 90-päevane retention pärast mis archivetakse S3'sse.
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,           -- NULL = anonymous / system
    actor_ip     INET,
    actor_ua     TEXT,
    action       VARCHAR(64) NOT NULL,    -- LOGIN, DESIGN_CREATE, ORDER_PLACE, ADMIN_*
    target_type  VARCHAR(64),             -- "design", "order", "user"
    target_id    BIGINT,
    outcome      VARCHAR(16) NOT NULL,    -- SUCCESS, FAILURE, DENIED
    details_json JSONB,
    request_id   VARCHAR(64),             -- X-Request-Id, joint with logs
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor_time ON audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_action_time ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id);

-- Pärast 90 päeva kantakse read S3 archive'i, siit kustutatakse
-- (eraldi Spring Batch job'ist, igaöine cron'iga).

COMMENT ON TABLE prompt_embeddings IS 'RAG-lite semantic cache — sarnased promptid taaskasutavad spec-i';
COMMENT ON TABLE audit_log IS 'Immutable audit trail kõigile mutation-tegevustele, 90p retention';
