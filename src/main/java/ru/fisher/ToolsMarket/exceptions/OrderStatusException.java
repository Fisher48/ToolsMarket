package ru.fisher.ToolsMarket.exceptions;

// Некорректный статус
public class OrderStatusException extends OrderException {
    private final String currentStatus;
    private final String targetStatus;

    public OrderStatusException(String message) {
        super(message);
        this.currentStatus = null;
        this.targetStatus = null;
    }

    public OrderStatusException(String currentStatus, String targetStatus, String message) {
        super(message);
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public OrderStatusException(String message, Throwable cause) {
        super(message, cause);
        this.currentStatus = null;
        this.targetStatus = null;
    }
}
