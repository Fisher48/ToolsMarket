-- Удаление товара, на который ссылаются заказы или корзины, больше не падает.
--
-- order_item объявлял product_id NOT NULL, хотя FK на product(id) был объявлен
-- как ON DELETE SET NULL. Противоречие: SET NULL не мог сработать, и удаление
-- падало с 23502 «null value in column "product_id" ... violates not-null
-- constraint». NOT NULL снимаем — тогда FK отрабатывает как и задумано.
--
-- cart_item ссылался на product(id) вообще без ON DELETE (то есть NO ACTION),
-- поэтому удаление товара, который ещё лежит у кого-то в корзине, тоже было
-- 500. Позиция корзины удалённого товара бессмысленна, поэтому CASCADE.
--
-- Заказы переживают удаление товара: product_name, product_sku и unit_price в
-- order_item — это snapshot, а product_id становится NULL.

ALTER TABLE order_item ALTER COLUMN product_id DROP NOT NULL;

-- Имя FK на cart_item(product_id) завязано на дефолтное именование PostgreSQL,
-- поэтому ищем его в pg_constraint, а не полагаемся на картинку из V5.
DO $$
DECLARE
    fk_name TEXT;
BEGIN
    FOR fk_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE t.relname = 'cart_item'
          AND c.contype = 'f'
          AND c.conkey = ARRAY(
                SELECT attnum
                FROM pg_attribute
                WHERE attrelid = t.oid
                  AND attname = 'product_id')::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE cart_item DROP CONSTRAINT %I', fk_name);
    END LOOP;
END $$;

ALTER TABLE cart_item
    ADD CONSTRAINT cart_item_product_id_fkey
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE;
