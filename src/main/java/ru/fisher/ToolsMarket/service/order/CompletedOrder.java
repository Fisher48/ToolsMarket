package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record CompletedOrder(Order order) implements OrderState {

    public CompletedOrder {
        if (order == null) {
            throw new IllegalArgumentException("Заказ не может быть null");
        }
    }

    @Override
    public boolean cancellable() {
        return false;
    }

    @Override
    public OrderState moveTo(OrderStatus target) {
        throw new OrderFinalizedException("COMPLETED");
    }

    @Override
    public OrderState cancel() {
        throw new OrderFinalizedException("COMPLETED");
    }
}