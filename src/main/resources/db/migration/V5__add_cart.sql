-- == Cart table ==
CREATE TABLE cart (
    id BIGSERIAL PRIMARY KEY,

    -- если пользователь авторизован
    user_id BIGINT,

    -- если гость
    session_id VARCHAR(255),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- хотя бы одно поле должно быть заполнено
    CONSTRAINT cart_user_or_session CHECK (
        (user_id IS NOT NULL) OR (session_id IS NOT NULL)
    )
);

CREATE INDEX idx_cart_user ON cart(user_id);
CREATE INDEX idx_cart_session ON cart(session_id);

-- == Cart Item ==
CREATE TABLE cart_item (
    id BIGSERIAL PRIMARY KEY,

    cart_id BIGINT NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id),

    -- для отображения без обращения к продукту
    product_name VARCHAR(512) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,

    -- Фиксируем характеристики продукта (если нужно)
    product_attributes JSONB,
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_cart_item_cart ON cart_item(cart_id);