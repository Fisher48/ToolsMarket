-- Добавляем новые колонки в order_items
ALTER TABLE order_item
    ADD COLUMN IF NOT EXISTS original_unit_price DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS discount_percentage DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS has_discount BOOLEAN DEFAULT FALSE;

-- Обновляем существующие записи
UPDATE order_item
SET
    original_unit_price = unit_price,
    has_discount = false
WHERE original_unit_price IS NULL;

-- Создаем индекс для быстрого поиска заказов со скидками
CREATE INDEX IF NOT EXISTS idx_order_items_has_discount ON order_item(has_discount);