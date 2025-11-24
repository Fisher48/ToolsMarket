CREATE TABLE attribute (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        unit VARCHAR(100),
                        type VARCHAR(50) NOT NULL,
                        sort_order INT DEFAULT 0,
                        options VARCHAR(1024),
                        category_id BIGINT NOT NULL,
                        required BOOLEAN DEFAULT FALSE,
                        filterable BOOLEAN DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE product_attribute_values (
                                      id BIGSERIAL PRIMARY KEY,
                                      product_id BIGINT NOT NULL,
                                      attribute_id BIGINT NOT NULL,
                                      value VARCHAR(1024),
                                      sort_order INT DEFAULT 0,
                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
                                      FOREIGN KEY (attribute_id) REFERENCES attribute(id) ON DELETE CASCADE,
                                      CONSTRAINT unique_product_attribute UNIQUE (product_id, attribute_id)
);