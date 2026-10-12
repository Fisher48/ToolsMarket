package ru.fisher.ToolsMarket.service.email;

import ru.fisher.ToolsMarket.dto.OrderDTO.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.UserDTO.UserRegistrationEvent;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class EmailNotificationFactory {

    private static final DateTimeFormatter ORDER_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter REGISTRATION_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private EmailNotificationFactory() {
    }

    public static PendingEmail createOrderEmail(
            OrderCreatedEvent event,
            String from,
            String adminEmail,
            SpringTemplateEngine templateEngine
    ) {
        return build(
                "email/order-created",
                "Новый заказ инструментов #" + event.orderNumber(),
                ORDER_DATE,
                context -> {
                    context.setVariable("order", event);
                    context.setVariable("items", event.orderItems());
                    context.setVariable("total", event.total());
                    context.setVariable("note", event.note());
                },
                adminEmail,
                templateEngine
        );
    }

    public static PendingEmail createRegistrationEmail(
            UserRegistrationEvent event,
            String from,
            String adminEmail,
            SpringTemplateEngine templateEngine
    ) {
        return build(
                "email/user-registration",
                "Новый пользователь зарегистрировался на ToolsMarket48",
                REGISTRATION_DATE,
                context -> context.setVariable("user", event),
                adminEmail,
                templateEngine
        );
    }

    /**
     * Полиморфный билдер письма: форма одна для всех уведомлений, а шаблон,
     * тема, формат даты и наполнение контекста передаются параметрами. Что именно
     * подставлять в письмо — это {@link Consumer} на {@link Context}, поэтому
     * новое уведомление добавляется данными, а не копией логики.
     */
    private static PendingEmail build(
            String template,
            String subject,
            DateTimeFormatter dateFormat,
            Consumer<Context> populate,
            String adminEmail,
            SpringTemplateEngine templateEngine
    ) {
        Context context = new Context();
        context.setVariable("formattedDate", LocalDateTime.now().format(dateFormat));
        populate.accept(context);

        String htmlContent = templateEngine.process(template, context);
        return new PendingEmail(adminEmail, subject, htmlContent);
    }
}
