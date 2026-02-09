package ru.fisher.ToolsMarket.models;

public enum UserType {
    DISTRIBUTOR("Дистрибьютор"),
    WHOLESALER("ОПТ"),
    VIP("ВИП"),
    SETY("Сети"),
    PARTNER("Партнер"),
    PARTNER_MAX("ПартнерМакс"),
    REGULAR("Обычный"); // Добавил для стандартных пользователей

    private final String displayName;

    UserType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
