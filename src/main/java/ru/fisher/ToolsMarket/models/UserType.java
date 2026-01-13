package ru.fisher.ToolsMarket.models;

public enum UserType {
    WHOLESALER("Оптовик"),
    VIP("VIP"),
    MAXIMUM("Максимальная"),
    PARTNER("Партнер"),
    REGULAR("Обычный"); // Добавил для стандартных пользователей

    private final String displayName;

    UserType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
