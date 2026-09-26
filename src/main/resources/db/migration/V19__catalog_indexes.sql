-- Индексы для страниц каталога и корзины.
--
-- Главный из них — обратный индекс в таблице-связи product_category. Primary key
-- там объявлен как (product_id, category_id), то есть индекса с category_id
-- впереди не было вообще: выборка товаров категории читала всю таблицу связей
-- целиком. На проде это был самый дорогой источник buffer traffic в каталоге.

-- 1. Каталог: товары одной категории (CategoryJdbcRepository.findProducts)
CREATE INDEX IF NOT EXISTS idx_product_category_category_product
    ON product_category (category_id, product_id);

-- 2. Основное фото товара: покрывает и подзапрос с ORDER BY sort_order LIMIT 1
--    (раньше сортировка выполнялась на каждую строку товара)
CREATE INDEX IF NOT EXISTS idx_product_image_product_sort
    ON product_image (product_id, sort_order);

-- 3. Корзина на странице каталога: джойн идёт по product_id и cart_id
--    одновременно, а существующий индекс покрывает только cart_id
CREATE INDEX IF NOT EXISTS idx_cart_item_cart_product
    ON cart_item (cart_id, product_id);

-- 4. История заказов пользователя: сортировка по дате убыв.
--    Имя таблицы в кавычках: order — зарезервированное слово
CREATE INDEX IF NOT EXISTS idx_orders_user_created
    ON "order" (user_id, created_at DESC);
