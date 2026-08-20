-- V1: Products tablosunu oluştur
CREATE TABLE products (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    available_stock INTEGER      NOT NULL DEFAULT 0 CHECK (available_stock >= 0),
    reserved_stock  INTEGER      NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_name ON products (name);

-- Seed data: Örnek ürünler
INSERT INTO products (id, name, available_stock, reserved_stock, version) VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'MacBook Pro 16"',   50,  0, 0),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'iPhone 15 Pro',    200,  0, 0),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'AirPods Pro 2',    500,  0, 0),
    ('d4e5f6a7-b8c9-0123-defa-234567890123', 'iPad Air M2',      100,  0, 0),
    ('e5f6a7b8-c9d0-1234-efab-345678901234', 'Apple Watch Ultra',  75, 0, 0);
