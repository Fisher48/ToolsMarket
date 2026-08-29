package ru.fisher.ToolsMarket.service.email;

@FunctionalInterface
public interface EmailSendAction {
    void execute(String recipient, String subject, String htmlContent) throws Exception;
}
