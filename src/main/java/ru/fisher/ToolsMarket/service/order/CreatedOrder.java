package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record CreatedOrder(Order order) implements OrderState {

    public CreatedOrder {
        if (order == null) {
            throw new IllegalArgumentException("Заказ не может быть null");
        }
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.CREATED;
    }

    public ProcessingOrder process() {
        return new ProcessingOrder(saveTransition(OrderStatus.PROCESSING));
    }

    public CancelledOrder cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}
