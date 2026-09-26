-- Удаление индексов, которые заведомо дублируют другие.
--
-- Здесь нет оглядки на pg_stat_user_indexes: оба индекса являются строгими
-- префиксами других, поэтому даже если планировщик их не выбирал, пользы от них
-- не было. Индексы, которые просто не использовались (idx_cart_session,
-- idx_order_items_has_discount), здесь намеренно не трогаем: их нужно сначала
-- перепроверить по статистике после рестарта контейнера.

-- 1. UNIQUE(user_type, product_type) из V10__add_discount_rules.sql уже даёт
--    индекс с тем же левым префиксом user_type
DROP INDEX IF EXISTS idx_user_discounts_user_type;

-- 2. Полностью покрывается idx_product_image_product_sort из V19:
--    (product_id) — префикс (product_id, sort_order)
DROP INDEX IF EXISTS idx_productimage_product;
