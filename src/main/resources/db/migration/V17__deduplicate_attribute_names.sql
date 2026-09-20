-- В таблице attribute нет уникального ограничения (category_id, name), из-за
-- чего исторические версии YML-импорта могли создать несколько атрибутов с одним
-- именем в одной категории (например, категория 44, 'Мощность' x2). Это роняло
-- импорт: Collectors.toMap в StemYmlImportService падал с IllegalStateException
-- 'Duplicate key 44_Мощность', а find... по (category_id, name) — с
-- IncorrectResultSizeDataAccessException.
--
-- Миграция: нормализуем имена (TRIM), для каждой группы (category_id, name)
-- оставляем одного канонического представителя (приоритет filterable/required,
-- затем минимальный id), переносим значения product_attribute_values с дублей
-- на выжившего представителя и закрываем причину уникальным ограничением.

-- 1. Нормализуем имена: 'Мощность ' и 'Мощность' — это один и тот же атрибут.
UPDATE attribute SET name = BTRIM(name)
WHERE name <> BTRIM(name);

-- 2. Канонический представитель каждой группы: сначала filterable, затем
--    required, затем минимальный id. Временная таблица живёт до конца сессии
--    (без ON COMMIT DROP — иначе psql/автокоммит удалили бы её сразу после CREATE).
CREATE TEMP TABLE attribute_keeper AS
SELECT DISTINCT ON (category_id, name)
       category_id, name, id AS keep_id
FROM attribute
ORDER BY category_id, name,
         (CASE WHEN filterable THEN 2 WHEN required THEN 1 ELSE 0 END) DESC,
         id ASC;

-- 3. У товара, где уже есть значение выжившего атрибута, значение атрибута-дубля
--    удаляем (иначе перенос упрётся в unique_product_attribute).
DELETE FROM product_attribute_values pav
WHERE pav.id IN (
    SELECT t.pav_id
    FROM (
        SELECT pav.id AS pav_id,
               ROW_NUMBER() OVER (
                   PARTITION BY pav.product_id, k.keep_id
                   ORDER BY (pav.attribute_id = k.keep_id) DESC, pav.attribute_id ASC
               ) AS rn
        FROM product_attribute_values pav
        JOIN attribute a ON a.id = pav.attribute_id
        JOIN attribute_keeper k ON k.category_id = a.category_id AND k.name = a.name
    ) t
    WHERE t.rn > 1
);

-- 4. Переносим оставшиеся значения дублей на канонического представителя.
UPDATE product_attribute_values pav
SET attribute_id = k.keep_id
FROM attribute a
JOIN attribute_keeper k ON k.category_id = a.category_id AND k.name = a.name
WHERE pav.attribute_id = a.id
  AND pav.attribute_id <> k.keep_id;

-- 5. Удаляем атрибуты-дубли.
DELETE FROM attribute a
USING attribute_keeper k
WHERE a.category_id = k.category_id
  AND a.name = k.name
  AND a.id <> k.keep_id;

-- 6. Закрываем проблему навсегда.
ALTER TABLE attribute
    ADD CONSTRAINT uk_attribute_category_name UNIQUE (category_id, name);

DROP TABLE IF EXISTS attribute_keeper;