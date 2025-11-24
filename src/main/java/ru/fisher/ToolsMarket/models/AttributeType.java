package ru.fisher.ToolsMarket.models;

public enum AttributeType {
    STRING("Текст"),
    NUMBER("Число"),
    BOOLEAN("Да/Нет"),
    SELECT("Выбор из списка");

    private final String displayName;

    AttributeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
