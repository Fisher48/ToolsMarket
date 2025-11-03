-- === Category ===
CREATE TABLE category (
                          id BIGSERIAL PRIMARY KEY,
                          title VARCHAR(255) NOT NULL UNIQUE,
                          name VARCHAR(255) NOT NULL,
                          parent_id BIGINT NULL REFERENCES category(id) ON DELETE SET NULL,
                          description TEXT,
                          sort_order INT DEFAULT 0,
                          created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_category_parent ON category(parent_id);

-- === Product ===
CREATE TABLE product (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(512) NOT NULL,
                         title VARCHAR(512) NOT NULL UNIQUE,
                         short_description VARCHAR(1024),
                         description TEXT,
                         sku VARCHAR(100) UNIQUE,
                         price NUMERIC(12,2) NOT NULL DEFAULT 0,
                         currency VARCHAR(3) NOT NULL DEFAULT 'RUB',
                         active BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at TIMESTAMP DEFAULT now(),
                         updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_product_active ON product(active);
CREATE INDEX idx_product_name_title ON product(name, title);

-- === Product ↔ Category (Many-to-Many) ===
CREATE TABLE product_category (
                                  product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
                                  category_id BIGINT NOT NULL REFERENCES category(id) ON DELETE CASCADE,
                                  PRIMARY KEY (product_id, category_id)
);

-- === ProductImage ===
CREATE TABLE product_image (
                               id BIGSERIAL PRIMARY KEY,
                               product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
                               url TEXT NOT NULL,
                               alt VARCHAR(255),
                               sort_order INT DEFAULT 0
);

-- === Индексы для быстрого поиска ===
CREATE INDEX idx_productimage_product ON product_image(product_id);