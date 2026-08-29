package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
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
    public OrderState moveTo(OrderStatus target) {
        return switch (target) {
            case PAID       -> new PaidOrder(saveTransition(OrderStatus.PAID));
            case COMPLETED  -> new CompletedOrder(saveTransition(OrderStatus.COMPLETED));
            case CANCELLED  -> cancel();
            default -> throw new InvalidStatusTransitionException(
                    OrderStatus.PROCESSING.name(), target.name());
        };
    }

    @Override
    public OrderState cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}