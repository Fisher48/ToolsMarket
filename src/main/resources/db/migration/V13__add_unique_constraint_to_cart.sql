-- Только одна корзина для одного пользователя
ALTER TABLE cart ADD CONSTRAINT unique_user_cart UNIQUE (user_id);