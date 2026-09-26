-- Полнотекстовый поиск по каталогу.
--
-- Раньше поиск был like '%q%' с OR по name, short_description, description, sku.
-- description — это TEXT, поэтому такой запрос всегда шёл seq scan по всей
-- таблице и стоил десятки миллисекунд.
--
-- Теперь поиск идёт по полнотекстовому вектору с GIN-индексом. Конфигурация
-- 'simple' — без стемминга и без словаря, поэтому русские окончания не
-- отбрасываются, а регистр не важен. Вес: имя и артикул важнее описания.
--
-- Вектор сгенерирован на стороне PostgreSQL (GENERATED ... STORED), поэтому
-- приложению не нужно его ни заполнять, ни обновлять: INSERT/UPDATE из
-- импортёров и админки продолжают работать без изменений.

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('simple'::regconfig, coalesce(name, '')), 'A') ||
            setweight(to_tsvector('simple'::regconfig, coalesce(sku, '')), 'A') ||
            setweight(to_tsvector('simple'::regconfig, coalesce(short_description, '')), 'B') ||
            setweight(to_tsvector('simple'::regconfig, coalesce(description, '')), 'C')
        ) STORED;

CREATE INDEX IF NOT EXISTS idx_product_search_vector ON product USING gin (search_vector);
