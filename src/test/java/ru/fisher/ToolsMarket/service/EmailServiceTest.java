package ru.fisher.ToolsMarket.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderItemDto;
import ru.fisher.ToolsMarket.dto.UserDTO.UserRegistrationEvent;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.FailedEmailRepository;
import ru.fisher.ToolsMarket.service.email.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private SpringTemplateEngine templateEngine;

    @Autowired
    private FailedEmailRepository failedEmailRepository;

    @BeforeEach
    void setUp() {
        failedEmailRepository.deleteAll();
        reset(mailSender, templateEngine);
    }

    @Test
    @DisplayName("Успешная отправка email")
    void sendOrderCreatedEmail_Success() throws Exception {
        OrderCreatedEvent event = createTestEvent();
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        emailService.sendOrderCreatedEmail(event);

        verify(mailSender, times(1)).send(mockMessage);
        verify(templateEngine, times(1)).process(eq("email/order-created"), any());
        assertThat(failedEmailRepository.count()).isZero();
    }

    @Test
    @DisplayName("Ошибка отправки - 3 попытки, потом fallback в failed_emails")
    void sendOrderCreatedEmail_RetryOnFailure() throws Exception {
        OrderCreatedEvent event = createTestEvent();
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(mockMessage);

        emailService.sendOrderCreatedEmail(event);

        verify(mailSender, times(3)).send(mockMessage);
        assertThat(failedEmailRepository.count()).isEqualTo(1);

        FailedEmail failed = failedEmailRepository.findAll().getFirst();
        assertThat(failed.getErrorMessage()).contains("SMTP error");
        assertThat(failed.getRecipient()).isEqualTo(event.customerEmail());
    }

    @Test
    @DisplayName("Успешная отправка после одной неудачи")
    void sendOrderCreatedEmail_SuccessAfterOneRetry() throws Exception {
        OrderCreatedEvent event = createTestEvent();
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        doThrow(new MailSendException("SMTP error"))
                .doNothing()
                .when(mailSender).send(mockMessage);

        emailService.sendOrderCreatedEmail(event);

        verify(mailSender, times(2)).send(mockMessage);
        assertThat(failedEmailRepository.count()).isZero();
    }

    @Test
    @DisplayName("State: PendingEmail -> SentEmail")
    void pendingEmailSendsSuccessfully() {
        PendingEmail pending = new PendingEmail("admin@test.com", "Test", "<html></html>");

        EmailNotificationState result = pending.send((r, s, h) -> {});

        assertThat(result).isInstanceOf(SentEmail.class);
        assertThat(result.recipient()).isEqualTo("admin@test.com");
    }

    @Test
    @DisplayName("State: PendingEmail -> FailedEmailState")
    void pendingEmailFails() {
        PendingEmail pending = new PendingEmail("admin@test.com", "Test", "<html></html>");

        EmailNotificationState result = pending.send((r, s, h) -> {
            throw new MailSendException("SMTP error");
        });

        assertThat(result).isInstanceOf(FailedEmailState.class);
        FailedEmailState failed = (FailedEmailState) result;
        assertThat(failed.errorMessage()).contains("SMTP error");
        assertThat(failed.failedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("State: FailedEmailState -> retry -> SentEmail")
    void failedEmailRetriesSuccessfully() {
        FailedEmailState failed = new FailedEmailState(
                "admin@test.com", "Test", "<html></html>", "SMTP error", 1);

        EmailNotificationState result = failed.retry((r, s, h) -> {});

        assertThat(result).isInstanceOf(SentEmail.class);
    }

    @Test
    @DisplayName("State: FailedEmailState -> retry -> FailedEmailState")
    void failedEmailRetriesFailsAgain() {
        FailedEmailState failed = new FailedEmailState(
                "admin@test.com", "Test", "<html></html>", "SMTP error", 1);

        EmailNotificationState result = failed.retry((r, s, h) -> {
            throw new MailSendException("SMTP error 2");
        });

        assertThat(result).isInstanceOf(FailedEmailState.class);
        FailedEmailState failedAgain = (FailedEmailState) result;
        assertThat(failedAgain.failedAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("State: SentEmail - терминальное, нет методов перехода")
    void sentEmailIsTerminal() {
        SentEmail sent = new SentEmail("admin@test.com", "Test");

        assertThat(sent.recipient()).isEqualTo("admin@test.com");
        assertThat(sent.subject()).isEqualTo("Test");
    }

    @Test
    @DisplayName("Отправка письма о регистрации")
    void sendUserRegistrationEmail_Success() throws Exception {
        var event = UserRegistrationEvent.builder()
                .userId(1L)
                .email("newuser@test.com")
                .username("newuser")
                .build();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Registration</html>");

        emailService.sendUserRegistrationEmail(event);

        verify(mailSender, times(1)).send(mockMessage);
        verify(templateEngine, times(1)).process(eq("email/user-registration"), any());
    }

    @Test
    @DisplayName("Ошибка отправки регистрации - retry и fallback")
    void sendUserRegistrationEmail_RetryOnFailure() throws Exception {
        var event = UserRegistrationEvent.builder()
                .userId(1L)
                .email("newuser@test.com")
                .username("newuser")
                .build();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Registration</html>");

        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(mockMessage);

        emailService.sendUserRegistrationEmail(event);

        verify(mailSender, times(3)).send(mockMessage);
    }

    @Test
    @DisplayName("Пустой список товаров - email все равно отправляется")
    void sendOrderCreatedEmail_EmptyOrderItems() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                1L, 1001L, new ArrayList<>(),
                BigDecimal.ZERO, "test@example.com",
                "TEST NOTE"
        );

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Empty order</html>");

        emailService.sendOrderCreatedEmail(event);

        verify(mailSender, times(1)).send(mockMessage);
        verify(templateEngine, times(1)).process(eq("email/order-created"), any());
    }

    @Test
    @DisplayName("Factory: createOrderEmail генерирует PendingEmail")
    void factoryCreatesPendingEmail() {
        OrderCreatedEvent event = createTestEvent();

        PendingEmail pending = EmailNotificationFactory.createOrderEmail(
                event, "from@test.com", "admin@test.com", templateEngine);

        assertThat(pending).isNotNull();
        assertThat(pending.recipient()).isEqualTo("admin@test.com");
        assertThat(pending.subject()).contains("123");
    }

    @Test
    @DisplayName("Factory: createRegistrationEmail генерирует PendingEmail")
    void factoryCreatesRegistrationEmail() {
        var event = UserRegistrationEvent.builder()
                .userId(1L)
                .email("user@test.com")
                .username("username")
                .build();

        PendingEmail pending = EmailNotificationFactory.createRegistrationEmail(
                event, "from@test.com", "admin@test.com", templateEngine);

        assertThat(pending).isNotNull();
        assertThat(pending.recipient()).isEqualTo("admin@test.com");
    }

    private OrderCreatedEvent createTestEvent() {
        Product mockProduct1 = Product.builder()
                .id(1L)
                .title("Молоток")
                .sku("HAMMER001")
                .productType(ProductType.TOOL)
                .images(new HashSet<>())
                .price(BigDecimal.valueOf(500))
                .build();

        Product mockProduct2 = Product.builder()
                .id(2L)
                .title("Отвертка")
                .sku("SCREWDRIVER001")
                .productType(ProductType.TOOL)
                .images(new HashSet<>())
                .price(BigDecimal.valueOf(300))
                .build();

        List<OrderItemDto> items = List.of(
                OrderItemDto.fromEntity(OrderItem.builder()
                        .product(mockProduct1)
                        .productName("Молоток")
                        .quantity(2)
                        .unitPrice(BigDecimal.valueOf(500))
                        .subtotal(BigDecimal.valueOf(1000))
                        .build()),
                OrderItemDto.fromEntity(OrderItem.builder()
                        .product(mockProduct2)
                        .productName("Отвертка")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(300))
                        .subtotal(BigDecimal.valueOf(300))
                        .build())
        );

        return new OrderCreatedEvent(
                1L,
                123L,
                items,
                BigDecimal.valueOf(1300),
                "test@example.com",
                "TEST NOTE"
        );
    }
}
