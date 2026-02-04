package ru.fisher.ToolsMarket.exceptions;

public class CaptchaException extends RuntimeException {
    public CaptchaException(String message) {
        super(message);
    }
}
