-- Добавляем предопределенные роли
INSERT INTO roles (name, description)
VALUES ('ROLE_USER', 'Обычный пользователь'),
       ('ROLE_ADMIN', 'Администратор'),
       ('ROLE_MANAGER', 'Менеджер');