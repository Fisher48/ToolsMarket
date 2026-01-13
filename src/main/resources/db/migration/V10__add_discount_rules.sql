-- Добавление типа пользователя
ALTER TABLE users ADD COLUMN user_type VARCHAR(50) DEFAULT 'REGULAR';

-- Добавление типа товара
ALTER TABLE product ADD COLUMN product_type VARCHAR(50) DEFAULT 'OTHER';

-- Таблица скидок
CREATE TABLE user_discounts (
                                id BIGSERIAL PRIMARY KEY,
                                user_type VARCHAR(50) NOT NULL,
                                product_type VARCHAR(50) NOT NULL,
                                discount_percentage DECIMAL(5,2),
                                is_active BOOLEAN DEFAULT true,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE(user_type, product_type)
);

-- Индексы для быстрого поиска
CREATE INDEX idx_user_discounts_user_type ON user_discounts(user_type);
CREATE INDEX idx_user_discounts_product_type ON user_discounts(product_type);