package ru.fisher.ToolsMarket.exceptions;

// Заказ не найден
public class OrderNotFoundException extends OrderException {
    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super(String.format("Заказ с ID %d не найден", orderId));
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
