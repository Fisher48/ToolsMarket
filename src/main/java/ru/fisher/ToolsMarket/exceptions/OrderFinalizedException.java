package ru.fisher.ToolsMarket.exceptions;

// Невозможно изменить статус завершенного/отмененного заказа
public class OrderFinalizedException extends OrderStatusException {
    public OrderFinalizedException(String status) {
        super(String.format("Невозможно изменить статус заказа: заказ уже %s",
                getStatusDisplayName(status)));
    }

    private static String getStatusDisplayName(String status) {
        return switch (status) {
            case "COMPLETED" -> "завершен";
            case "CANCELLED" -> "отменен";
            default -> status.toLowerCase();
        };
    }
}
