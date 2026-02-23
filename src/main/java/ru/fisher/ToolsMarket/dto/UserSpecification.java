package ru.fisher.ToolsMarket.dto;

import org.springframework.data.jpa.domain.Specification;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserType;

public class UserSpecification {

    // Поиск по всем полям
    public static Specification<User> searchAll(String query) {
        String likePattern = "%" + query.toLowerCase() + "%";

        // Нормализуем телефон для поиска
        String normalizedPhone = normalizePhone(query);
        String phoneLikePattern = normalizedPhone != null ? "%" + normalizedPhone + "%" : null;

        return (root, q, cb) -> {
            // Базовый поиск по имени, email
            var predicate = cb.or(
                    cb.like(cb.lower(root.get("username")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern),
                    cb.like(cb.lower(root.get("firstName")), likePattern),
                    cb.like(cb.lower(root.get("lastName")), likePattern)
            );

            // Добавляем поиск по телефону с нормализацией
            if (phoneLikePattern != null) {
                predicate = cb.or(predicate,
                        cb.like(cb.lower(root.get("phone")), phoneLikePattern)
                );
            }

            return predicate;
        };
    }

    // Метод для нормализации номера телефона
    private static String normalizePhone(String phone) {
        if (phone == null || phone.isEmpty()) return null;

        // Удаляем все нецифровые символы
        String digits = phone.replaceAll("[^0-9]", "");

        // Если номер начинается с 8, заменяем на 7
        if (digits.startsWith("8") && digits.length() >= 11) {
            digits = "7" + digits.substring(1);
        }

        return digits;
    }

    // Фильтр по типу пользователя
    public static Specification<User> hasUserType(UserType userType) {
        return (root, query, cb) ->
                cb.equal(root.get("userType"), userType);
    }

    // Фильтр по статусу
    public static Specification<User> hasStatus(Boolean enabled) {
        return (root, query, cb) ->
                cb.equal(root.get("enabled"), enabled);
    }
}
