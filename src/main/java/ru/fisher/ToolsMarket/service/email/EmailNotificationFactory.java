package ru.fisher.ToolsMarket.service.email;

import ru.fisher.ToolsMarket.dto.OrderDTO.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.UserDTO.UserRegistrationEvent;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class EmailNotificationFactory {

    private EmailNotificationFactory() {
    }

    public static PendingEmail createOrderEmail(
            OrderCreatedEvent event,
            String from,
            String adminEmail,
            SpringTemplateEngine templateEngine
    ) {
        Context context = new Context();
        context.setVariable("order", event);
        context.setVariable("items", event.orderItems());
        context.setVariable("total", event.total());
        context.setVariable("note", event.note());
        context.setVariable("formattedDate", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        String htmlContent = templateEngine.process("email/order-created", context);
        String subject = "Новый заказ инструментов #" + event.orderNumber();

        return new PendingEmail(adminEmail, subject, htmlContent);
    }

    public static PendingEmail createRegistrationEmail(
            UserRegistrationEvent event,
            String from,
            String adminEmail,
            SpringTemplateEngine templateEngine
    ) {
        Context context = new Context();
        context.setVariable("user", event);
        context.setVariable("formattedDate", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));

        String htmlContent = templateEngine.process("email/user-registration", context);
        String subject = "Новый пользователь зарегистрировался на ToolsMarket48";

        return new PendingEmail(adminEmail, subject, htmlContent);
    }
}
