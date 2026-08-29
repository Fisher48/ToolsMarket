package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record CancelledOrder(Order order) implements OrderState {

    public CancelledOrder {
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
        throw new OrderFinalizedException("CANCELLED");
    }

    @Override
    public OrderState cancel() {
        throw new OrderFinalizedException("CANCELLED");
    }
}