ALTER TABLE product
    ADD COLUMN created_by_user_id BIGINT,
    ADD COLUMN updated_by_user_id BIGINT;

-- Индекс для быстрого поиска
CREATE INDEX idx_products_created_by ON product(created_by_user_id);