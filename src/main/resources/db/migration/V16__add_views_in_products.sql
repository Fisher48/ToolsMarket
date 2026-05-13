ALTER TABLE product ADD COLUMN views BIGINT DEFAULT 0;

-- Обновляем существующие товары
UPDATE product SET views = 0 WHERE views IS NULL;