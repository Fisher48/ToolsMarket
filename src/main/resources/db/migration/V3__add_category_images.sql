-- Добавляем поля для изображений категорий
ALTER TABLE category
ADD COLUMN image_url VARCHAR(512),
ADD COLUMN thumbnail_url VARCHAR(512);

-- Комментарии к полям
COMMENT ON COLUMN category.image_url IS 'URL основного изображения категории';
COMMENT ON COLUMN category.thumbnail_url IS 'URL миниатюры изображения категории';