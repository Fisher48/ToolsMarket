package ru.fisher.ToolsMarket.exceptions;

// Базовое исключение для заказов
public class OrderException extends RuntimeException {
    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
