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
    public OrderStatus status() {
        return OrderStatus.CANCELLED;
    }

    public CancelledOrder process() {
        throw new OrderFinalizedException("CANCELLED");
    }

    public CancelledOrder pay() {
        throw new OrderFinalizedException("CANCELLED");
    }

    public CancelledOrder complete() {
        throw new OrderFinalizedException("CANCELLED");
    }

    public CancelledOrder cancel() {
        throw new OrderFinalizedException("CANCELLED");
    }
}
