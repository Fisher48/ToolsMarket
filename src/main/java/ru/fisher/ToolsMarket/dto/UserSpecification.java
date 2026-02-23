package ru.fisher.ToolsMarket.dto;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserType;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> searchAll(String query) {

        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        String trimmed = query.trim().toLowerCase();

        return (root, q, cb) -> {

            List<Predicate> wordPredicates = new ArrayList<>();

            String[] words = trimmed.split("\\s+");

            for (String word : words) {
                String like = "%" + word + "%";

                wordPredicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("username")), like),
                                cb.like(cb.lower(root.get("email")), like),
                                cb.like(cb.lower(root.get("firstName")), like),
                                cb.like(cb.lower(root.get("lastName")), like),
                                cb.like(cb.lower(root.get("phone")), like)
                        )
                );
            }

            Predicate textSearch = cb.and(wordPredicates.toArray(new Predicate[0]));

            // Телефон отдельно
            String normalizedPhone = normalizePhone(trimmed);

            if (normalizedPhone != null && !normalizedPhone.isEmpty()) {

                Expression<String> digitsOnlyPhone =
                        cb.function(
                                "regexp_replace",
                                String.class,
                                root.get("phone"),
                                cb.literal("[^0-9]"),
                                cb.literal(""),
                                cb.literal("g")
                        );

                Predicate phoneSearch =
                        cb.like(digitsOnlyPhone, "%" + normalizedPhone + "%");

                return cb.or(textSearch, phoneSearch);
            }

            return textSearch;
        };
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;

        String digits = phone.replaceAll("[^0-9]", "");

        if (digits.startsWith("8") && digits.length() >= 11) {
            digits = "7" + digits.substring(1);
        }

        return digits;
    }

    public static Specification<User> hasUserType(UserType userType) {
        return (root, query, cb) ->
                userType != null ? cb.equal(root.get("userType"), userType) : null;
    }

    public static Specification<User> hasStatus(Boolean enabled) {
        return (root, query, cb) ->
                enabled != null ? cb.equal(root.get("enabled"), enabled) : null;
    }
}
