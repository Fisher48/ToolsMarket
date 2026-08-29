package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record PaidOrder(Order order) implements OrderState {

    public PaidOrder {
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
        return OrderStatus.PAID;
    }

    public CompletedOrder complete() {
        return new CompletedOrder(saveTransition(OrderStatus.COMPLETED));
    }

    public CancelledOrder cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}
