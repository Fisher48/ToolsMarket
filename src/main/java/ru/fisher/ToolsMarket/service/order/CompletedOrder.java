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
    public OrderStatus status() {
        return OrderStatus.COMPLETED;
    }

    public CompletedOrder process() {
        throw new OrderFinalizedException("COMPLETED");
    }

    public CompletedOrder pay() {
        throw new OrderFinalizedException("COMPLETED");
    }

    public CompletedOrder complete() {
        throw new OrderFinalizedException("COMPLETED");
    }

    public CompletedOrder cancel() {
        throw new OrderFinalizedException("COMPLETED");
    }
}
