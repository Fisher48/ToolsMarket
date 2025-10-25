-- Тестовые категории
INSERT INTO category (title, name, description, sort_order)
VALUES
    ('elektroinstrumenty', 'Электроинструменты', 'Профессиональные электроинструменты', 1),
    ('dreli', 'Дрели', 'Аккумуляторные и сетевые дрели', 2),
    ('shurupoverty', 'Шуруповерты', 'Беспроводные шуруповерты', 3),
    ('perforatory', 'Перфораторы', 'Мощные перфораторы для строительства', 4);

-- Обновляем parent_id для подкатегорий
UPDATE category SET parent_id = (SELECT id FROM category WHERE title = 'elektroinstrumenty')
WHERE title IN ('dreli', 'shurupoverty', 'perforatory');

-- Тестовые товары
INSERT INTO product (name, title, short_description, description, sku, price, currency, active)
VALUES
('Дрель аккумуляторная Makita DDF453SYE', 'drel-makita-ddf453sye',
'Мощная аккумуляторная дрель с двумя батареями',
'Профессиональная аккумуляторная дрель-шуруповерт Makita DDF453SYE с двумя Li-Ion аккумуляторами 18 В, 1.5 А·ч. Максимальный крутящий момент 115 Н·м.',
'MAK-DDF453SYE', 12990.00, 'RUB', true),

('Шуруповерт Bosch GSR 120-LI', 'shurupovert-bosch-gsr120-li',
'Компактный шуруповерт для дома и ремонта',
'Беспроводной шуруповерт Bosch GSR 120-LI с аккумулятором 12 В. Идеален для сборки мебели и мелкого ремонта.',
'BOS-GSR120LI', 4590.00, 'RUB', true),

('Перфоратор DeWalt D25600K', 'perforator-dewalt-d25600k',
'Мощный перфоратор для профессионального использования',
'Перфоратор DeWalt D25600K с энергией удара 3.2 Дж. Подходит для бетона, кирпича и камня.',
'DEW-D25600K', 18990.00, 'RUB', true);

-- Связываем товары с категориями
INSERT INTO product_category (product_id, category_id)
VALUES
(
 (SELECT id FROM product WHERE title = 'drel-makita-ddf453sye'),
 (SELECT id FROM category WHERE title = 'dreli')
),
(
 (SELECT id FROM product WHERE title = 'drel-makita-ddf453sye'),
 (SELECT id FROM category WHERE title = 'elektroinstrumenty')
),
(
 (SELECT id FROM product WHERE title = 'shurupovert-bosch-gsr120-li'),
 (SELECT id FROM category WHERE title = 'shurupoverty')
),
(
 (SELECT id FROM product WHERE title = 'shurupovert-bosch-gsr120-li'),
 (SELECT id FROM category WHERE title = 'elektroinstrumenty')
),
(
 (SELECT id FROM product WHERE title = 'perforator-dewalt-d25600k'),
 (SELECT id FROM category WHERE title = 'perforatory')
),
(
 (SELECT id FROM product WHERE title = 'perforator-dewalt-d25600k'),
 (SELECT id FROM category WHERE title = 'elektroinstrumenty')
);