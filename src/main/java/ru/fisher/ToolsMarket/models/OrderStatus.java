package ru.fisher.ToolsMarket.models;

public enum OrderStatus {
    CREATED, PROCESSING, PAID, COMPLETED, CANCELLED;

    /**
     * Заказ завершён — дальше никакие переходы не допустимы.
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Заказ в работе (не завершён).
     */
    public boolean isActive() {
        return !isFinal();
    }

    /**
     * Может ли заказ быть отменён в этом статусе.
     * Единый источник правды — сюда делегируют и {@code Order.canBeCancelledBy()},
     * и state machine ({@code OrderState.cancellable()}).
     */
    public boolean isCancellable() {
        return this == CREATED || this == PROCESSING || this == PAID;
    }
}
