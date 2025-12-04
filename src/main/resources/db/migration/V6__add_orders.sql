-- === Order table ===
CREATE TABLE "order" (
    id BIGSERIAL PRIMARY KEY,
    order_number BIGINT NOT NULL UNIQUE,
    user_id BIGINT,
    status VARCHAR(255) NOT NULL DEFAULT 'CREATED',
    total_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_status ON "order"(status);
CREATE INDEX idx_orders_created_at ON "order"(created_at);

-- == Order Item ===
CREATE TABLE order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(512) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,

    -- Атрибуты товара (сохраняем snapshot)
    product_attributes JSONB,

    -- Количество и сумма
    quantity INT NOT NULL CHECK (quantity > 0),
    subtotal NUMERIC(12,2) NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (order_id) REFERENCES "order"(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE SET NULL
);

CREATE INDEX idx_order_item_order ON order_item(order_id);


