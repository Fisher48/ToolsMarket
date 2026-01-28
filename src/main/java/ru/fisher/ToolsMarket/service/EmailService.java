package ru.fisher.ToolsMarket.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.fisher.ToolsMarket.dto.OrderCreatedEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailService {

    public final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from}")
    public String from;

    @Value("${app.mail.admin}")
    private String adminEmail;

    public void sendOrderCreatedEmail(OrderCreatedEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(adminEmail);
            helper.setSubject("Новый заказ инструментов #" + event.orderNumber());

            // Контекст для Thymeleaf
            Context context = new Context();
            context.setVariable("order", event);
            context.setVariable("items", event.orderItems());
            context.setVariable("total", event.total());
            context.setVariable("formattedDate", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            String htmlContent = templateEngine.process("email/order-created", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

}
