CREATE TABLE IF NOT EXISTS payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);

-- Sipariş bazlı sorgular için index
CREATE INDEX idx_payments_order_id ON payments (order_id);
