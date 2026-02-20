-- Добавляем поле note в таблицу users
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS note TEXT;

-- Комментарий для понимания назначения поля
COMMENT ON COLUMN users.note IS 'Примечание/заметка о пользователе';