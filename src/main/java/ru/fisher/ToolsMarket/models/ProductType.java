package ru.fisher.ToolsMarket.models;

public enum ProductType {
    TOOL("Электро/Бензо/АКБ Инструмент"),
    HAND_TOOL("Ручник/Расходка"),
    LARGE_TOOL("Крупный инструмент/Техника"),
    OTHER("Прочие"),
    ZUBR("Зубр"),
    BORER("Борер"),
    SIMAR("Симар"),
    BRIGHT("Брайт"),
    ALTEKO("Альтеко"),
    KINGQUEEN("КингКуин"),
    FENGBAO("ФенгБао"),
    RUIBA("Руиба"),
    FOXWELD("ФоксВелд"),
    OASIS("Оазис");

    private final String displayName;

    ProductType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
