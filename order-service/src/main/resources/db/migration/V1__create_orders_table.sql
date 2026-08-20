-- V1: Orders tablosunu oluştur
CREATE TABLE orders (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   VARCHAR(255) NOT NULL,
    product_id    VARCHAR(255) NOT NULL,
    quantity      INTEGER      NOT NULL CHECK (quantity > 0),
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Sık sorgulanan alanlar için indeksler
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status ON orders (status);
