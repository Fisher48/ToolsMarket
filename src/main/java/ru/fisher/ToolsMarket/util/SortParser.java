package ru.fisher.ToolsMarket.util;

import org.springframework.data.domain.Sort;

public final class SortParser {

    private SortParser() {
    }

    public static Sort parse(String sort, Sort fallback) {
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        if (field.isEmpty()) {
            return fallback;
        }
        if (parts.length < 2 || parts[1].isBlank()) {
            return Sort.by(field).ascending();
        }
        try {
            Sort.Direction direction = Sort.Direction.fromString(parts[1].trim());
            return Sort.by(direction, field);
        } catch (IllegalArgumentException e) {
            return Sort.by(field).ascending();
        }
    }
}
