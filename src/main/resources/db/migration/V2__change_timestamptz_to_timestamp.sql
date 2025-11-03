-- Изменяем тип столбцов с TIMESTAMPTZ на TIMESTAMP для H2 совместимости

-- Создаем временные столбцы
ALTER TABLE category ADD COLUMN created_at_temp TIMESTAMP;
ALTER TABLE product ADD COLUMN created_at_temp TIMESTAMP;
ALTER TABLE product ADD COLUMN updated_at_temp TIMESTAMP;

-- Копируем данные из старых столбцов в новые
UPDATE category SET created_at_temp = created_at;
UPDATE product SET created_at_temp = created_at;
UPDATE product SET updated_at_temp = updated_at;

-- Удаляем старые столбцы
ALTER TABLE category DROP COLUMN created_at;
ALTER TABLE product DROP COLUMN created_at;
ALTER TABLE product DROP COLUMN updated_at;

-- Переименовываем временные столбцы
ALTER TABLE category RENAME COLUMN created_at_temp TO created_at;
ALTER TABLE product RENAME COLUMN created_at_temp TO created_at;
ALTER TABLE product RENAME COLUMN updated_at_temp TO updated_at;

-- Добавляем constraints обратно
ALTER TABLE category ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE category ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE product ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE product ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE product ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE product ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;