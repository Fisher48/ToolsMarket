package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record ProcessingOrder(Order order) implements OrderState {

    public ProcessingOrder {
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
        return OrderStatus.PROCESSING;
    }

    public PaidOrder pay() {
        return new PaidOrder(saveTransition(OrderStatus.PAID));
    }

    public CancelledOrder cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}
