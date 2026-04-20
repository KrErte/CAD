-- Gallery: public designs shared by users
CREATE TABLE gallery_designs (
    id          BIGSERIAL PRIMARY KEY,
    design_id   BIGINT NOT NULL REFERENCES designs(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    tags        VARCHAR(255),
    likes       INT NOT NULL DEFAULT 0,
    forks       INT NOT NULL DEFAULT 0,
    public      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gallery_likes ON gallery_designs(likes DESC);
CREATE INDEX idx_gallery_created ON gallery_designs(created_at DESC);
CREATE INDEX idx_gallery_user ON gallery_designs(user_id);

-- Gallery likes tracking (prevent double-likes)
CREATE TABLE gallery_likes (
    id          BIGSERIAL PRIMARY KEY,
    gallery_id  BIGINT NOT NULL REFERENCES gallery_designs(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(gallery_id, user_id)
);

-- Design version history
CREATE TABLE design_versions (
    id          BIGSERIAL PRIMARY KEY,
    design_id   BIGINT NOT NULL REFERENCES designs(id) ON DELETE CASCADE,
    version     INT NOT NULL DEFAULT 1,
    params      JSONB NOT NULL,
    summary_et  VARCHAR(255),
    stl         BYTEA,
    size_bytes  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_versions_design ON design_versions(design_id, version DESC);

-- Print orders
CREATE TABLE print_orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    design_id       BIGINT REFERENCES designs(id) ON DELETE SET NULL,
    material        VARCHAR(32) NOT NULL DEFAULT 'PLA',
    infill_pct      INT NOT NULL DEFAULT 20,
    quantity        INT NOT NULL DEFAULT 1,
    color           VARCHAR(32) DEFAULT 'must',
    shipping_name   VARCHAR(255),
    shipping_address TEXT,
    shipping_city   VARCHAR(128),
    shipping_zip    VARCHAR(16),
    shipping_country VARCHAR(3) DEFAULT 'EE',
    price_eur       NUMERIC(10,2) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'pending',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON print_orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status ON print_orders(status);

-- Add language preference to users
ALTER TABLE users ADD COLUMN lang VARCHAR(5) DEFAULT 'et';
