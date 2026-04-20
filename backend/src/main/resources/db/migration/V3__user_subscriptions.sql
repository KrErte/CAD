-- V3: Add user_subscriptions table and BUSINESS plan support.

CREATE TABLE user_subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    stripe_customer_id      VARCHAR(255),
    stripe_subscription_id  VARCHAR(255),
    plan                    VARCHAR(16) NOT NULL DEFAULT 'FREE',
    status                  VARCHAR(32) NOT NULL DEFAULT 'active',
    current_period_end      TIMESTAMPTZ,
    model_count             INT NOT NULL DEFAULT 0,
    model_limit             INT NOT NULL DEFAULT 3,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_sub_stripe_customer ON user_subscriptions(stripe_customer_id);
CREATE INDEX idx_user_sub_stripe_sub ON user_subscriptions(stripe_subscription_id);

-- Migrate existing subscription data from users table into user_subscriptions.
INSERT INTO user_subscriptions (user_id, stripe_customer_id, stripe_subscription_id, plan, status, current_period_end, model_limit)
SELECT id, stripe_customer_id, stripe_subscription_id, plan, 'active', plan_active_until,
       CASE plan WHEN 'PRO' THEN 50 WHEN 'BUSINESS' THEN 200 ELSE 3 END
FROM users
WHERE stripe_subscription_id IS NOT NULL;
