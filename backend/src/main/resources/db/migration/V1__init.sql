CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    name                   VARCHAR(255),
    google_sub             VARCHAR(255) UNIQUE,
    plan                   VARCHAR(16) NOT NULL DEFAULT 'FREE',
    stripe_customer_id     VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    plan_active_until      TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE usage_monthly (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    year_month VARCHAR(7) NOT NULL,
    stl_count  INT NOT NULL DEFAULT 0,
    UNIQUE (user_id, year_month)
);

CREATE INDEX idx_usage_user_month ON usage_monthly(user_id, year_month);
CREATE INDEX idx_users_stripe_customer ON users(stripe_customer_id);
