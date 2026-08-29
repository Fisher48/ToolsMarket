package ru.fisher.ToolsMarket.service.email;

public record PendingEmail(
        String recipient,
        String subject,
        String htmlContent
) implements EmailNotificationState {

    public EmailNotificationState send(EmailSendAction action) {
        try {
            action.execute(recipient, subject, htmlContent);
            return new SentEmail(recipient, subject);
        } catch (Exception ex) {
            return new FailedEmailState(
                    recipient, subject, htmlContent,
                    ex.getMessage(), 1
            );
        }
    }
}
