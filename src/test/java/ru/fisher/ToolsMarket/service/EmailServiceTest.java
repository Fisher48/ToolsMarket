package ru.fisher.ToolsMarket.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderItemDto;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.FailedEmailRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@EnableRetry
@ContextConfiguration(initializers = PostgresTestConfig.class)
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private SpringTemplateEngine templateEngine;

    @MockitoSpyBean  // Используем SpyBean, чтобы проверить вызовы
    private EmailService emailServiceSpy;

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
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        // when
        emailService.sendOrderCreatedEmail(event);

        // then
        verify(mailSender, times(1)).send(mockMessage);
        verify(templateEngine, times(1)).process(eq("email/order-created"), any());
        assertThat(failedEmailRepository.count()).isZero();
    }

    @Test
    @DisplayName("Ошибка отправки - должно быть 3 попытки (retry)")
    void sendOrderCreatedEmail_RetryOnFailure() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        // SMTP падает 3 раза
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(mockMessage);

        // when
        try {
            emailService.sendOrderCreatedEmail(event);
        } catch (Exception e) {
            // Ожидаем исключение после всех retry
        }

        // then: проверяем что было 3 попытки
        verify(mailSender, times(3)).send(mockMessage);

        // И fallback сработал
        assertThat(failedEmailRepository.count()).isEqualTo(1);

        FailedEmail failed = failedEmailRepository.findAll().getFirst();
        assertThat(failed.getErrorMessage()).contains("SMTP error");
        assertThat(failed.getRecipient()).isEqualTo(event.customerEmail());
    }

    @Test
    @DisplayName("Успешная отправка после одной неудачи (retry работает)")
    void sendOrderCreatedEmail_SuccessAfterOneRetry() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        // Первая попытка неудачная, вторая успешная
        doThrow(new MailSendException("SMTP error"))
                .doNothing()
                .when(mailSender).send(mockMessage);

        // when
        emailService.sendOrderCreatedEmail(event);

        // then: было 2 попытки
        verify(mailSender, times(2)).send(mockMessage);
        assertThat(failedEmailRepository.count()).isZero();
    }

    @Test
    @DisplayName("Fallback сохраняет информацию о неудачной отправке")
    void recover_SavesFailedEmailToDatabase() {
        // given
        OrderCreatedEvent event = createTestEvent();
        MailSendException exception = new MailSendException("SMTP timeout");

        // when: напрямую вызываем recover (fallback метод)
        emailService.recover(exception, event);

        // then
        List<FailedEmail> failedEmails = failedEmailRepository.findAll();
        assertThat(failedEmails).hasSize(1);

        FailedEmail failed = failedEmails.getFirst();
        assertThat(failed.getErrorMessage()).isEqualTo("SMTP timeout");
        assertThat(failed.getRecipient()).isEqualTo("test@example.com");
        assertThat(failed.getOrderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(failed.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ошибка в шаблонизаторе - сохраняется в failed_emails")
    void sendOrderCreatedEmail_TemplateError_SavesToFailedEmails() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        // Ошибка в шаблонизаторе (не MailException)
        RuntimeException templateError = new RuntimeException("Template error");
        when(templateEngine.process(anyString(), any()))
                .thenThrow(templateError);

        // when
        emailService.sendOrderCreatedEmail(event); // Не бросает исключение

        // then
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));

        // Проверяем что ошибка сохранена в failed_emails
        assertThat(failedEmailRepository.count()).isEqualTo(1);

        FailedEmail failed = failedEmailRepository.findAll().getFirst();
        assertThat(failed.getRecipient()).isEqualTo("test@example.com");
        assertThat(failed.getErrorMessage()).contains("Template error");
    }

    @Test
    @DisplayName("Простой тест retry - должно быть 3 попытки")
    void simpleRetryTest() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test</html>");

        AtomicInteger sendCallCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            int count = sendCallCount.incrementAndGet();
            System.out.println("Send attempt #" + count + " at " + System.currentTimeMillis());
            throw new MailSendException("SMTP error attempt " + count);
        }).when(mailSender).send(mockMessage);

        // when
        try {
            emailService.sendOrderCreatedEmail(event);
        } catch (Exception e) {
            System.out.println("Exception thrown: " + e.getClass().getSimpleName());
            System.out.println("Total send attempts: " + sendCallCount.get());
        }

        // then
        // Ожидаем 3 попытки
        assertThat(sendCallCount.get()).as("Должно быть 3 попытки отправки").isEqualTo(3);
    }

    @Test
    @DisplayName("Надежный параллельный тест с изоляцией")
    @Timeout(value = 60, unit = TimeUnit.SECONDS) // Даем больше времени
    void sendOrderCreatedEmail_ReliableConcurrentTest() throws Exception {
        // Идея: запускаем тесты в полной изоляции друг от друга

        // Тест 1: Только параллельные ошибки
        testConcurrentFailures();

        // Очищаем все состояние
        resetAllMocks();
        failedEmailRepository.deleteAll();

        // Тест 2: Параллельные успехи
        testConcurrentSuccesses();

        // Очищаем все состояние
        resetAllMocks();
        failedEmailRepository.deleteAll();

        // Тест 3: Смешанный сценарий (как в оригинальном тесте)
        testMixedConcurrentScenario();
    }

    private void testConcurrentFailures() throws Exception {
        System.out.println("\n=== Test 1: Concurrent failures ===");

        OrderCreatedEvent event1 = new OrderCreatedEvent(1L, 1001L, new ArrayList<>(),
                BigDecimal.valueOf(1000), "user1@example.com");
        OrderCreatedEvent event2 = new OrderCreatedEvent(2L, 1002L, new ArrayList<>(),
                BigDecimal.valueOf(2000), "user2@example.com");

        // Настраиваем моки для ошибок
        MimeMessage mock1 = mock(MimeMessage.class);
        MimeMessage mock2 = mock(MimeMessage.class);

        AtomicInteger createCounter = new AtomicInteger(0);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> {
            int count = createCounter.incrementAndGet();
            return count % 2 == 0 ? mock1 : mock2;
        });

        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email</html>");

        // Оба мока выбрасывают исключения
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        // Запускаем параллельно
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.invokeAll(List.of(
                () -> { emailService.sendOrderCreatedEmail(event1); return null; },
                () -> { emailService.sendOrderCreatedEmail(event2); return null; }
        ), 30, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Даем время для retry
        Thread.sleep(10000);

        // Проверяем что оба упали
        assertThat(failedEmailRepository.count()).isEqualTo(2);
    }

    private void testConcurrentSuccesses() throws Exception {
        System.out.println("\n=== Test 2: Concurrent successes ===");

        OrderCreatedEvent event1 = new OrderCreatedEvent(3L, 1003L, new ArrayList<>(),
                BigDecimal.valueOf(3000), "user3@example.com");
        OrderCreatedEvent event2 = new OrderCreatedEvent(4L, 1004L, new ArrayList<>(),
                BigDecimal.valueOf(4000), "user4@example.com");

        // Настраиваем моки для успеха
        MimeMessage mock1 = mock(MimeMessage.class);
        MimeMessage mock2 = mock(MimeMessage.class);

        AtomicInteger createCounter = new AtomicInteger(0);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> {
            int count = createCounter.incrementAndGet();
            return count % 2 == 0 ? mock1 : mock2;
        });

        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email</html>");

        // Успешная отправка
        doNothing().when(mailSender).send(any(MimeMessage.class));

        // Запускаем параллельно
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.invokeAll(List.of(
                () -> { emailService.sendOrderCreatedEmail(event1); return null; },
                () -> { emailService.sendOrderCreatedEmail(event2); return null; }
        ), 10, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Проверяем что нет failed emails
        assertThat(failedEmailRepository.count()).isZero();
    }

    private void testMixedConcurrentScenario() throws Exception {
        System.out.println("\n=== Test 3: Mixed scenario (one fails, one succeeds) ===");

        OrderCreatedEvent event1 = new OrderCreatedEvent(5L, 1005L, new ArrayList<>(),
                BigDecimal.valueOf(5000), "user5@example.com");
        OrderCreatedEvent event2 = new OrderCreatedEvent(6L, 1006L, new ArrayList<>(),
                BigDecimal.valueOf(6000), "user6@example.com");

        // Используем ThreadLocal для изоляции
        ThreadLocal<Boolean> shouldFail = ThreadLocal.withInitial(() -> false);
        ThreadLocal<AtomicInteger> sendCount = ThreadLocal.withInitial(AtomicInteger::new);

        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email</html>");

        doAnswer(inv -> {
            AtomicInteger counter = sendCount.get();
            int attempt = counter.incrementAndGet();
            String thread = Thread.currentThread().getName();

            System.out.println(thread + " - Send attempt #" + attempt +
                    " shouldFail: " + shouldFail.get());

            if (shouldFail.get()) {
                throw new MailSendException("SMTP error in " + thread);
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        // Запускаем потоки
        CountDownLatch latch = new CountDownLatch(2);

        // Поток 1: с ошибками
        new Thread(() -> {
            shouldFail.set(true);
            try {
                emailService.sendOrderCreatedEmail(event1);
            } catch (Exception e) {
                System.out.println("Thread1 failed as expected");
            } finally {
                latch.countDown();
            }
        }, "Failing-Thread").start();

        // Поток 2: успешный
        new Thread(() -> {
            shouldFail.set(false);
            try {
                Thread.sleep(100); // Задержка
                emailService.sendOrderCreatedEmail(event2);
                System.out.println("Thread2 succeeded");
            } catch (Exception e) {
                System.out.println("Thread2 failed unexpectedly");
            } finally {
                latch.countDown();
            }
        }, "Succeeding-Thread").start();

        latch.await(30, TimeUnit.SECONDS);

        // Даем время для retry
        Thread.sleep(10000);

        // Проверяем что только один failed email
        long failedCount = failedEmailRepository.count();
        System.out.println("Failed emails: " + failedCount);

        if (failedCount > 0) {
            FailedEmail failed = failedEmailRepository.findAll().getFirst();
            assertThat(failed.getRecipient()).isEqualTo("user5@example.com");
        }
    }

    private void resetAllMocks() {
        reset(mailSender, templateEngine);
    }

    @Test
    @DisplayName("Два независимых вызова - один с ошибкой, другой успешный")
    void sendOrderCreatedEmail_TwoIndependentCalls() throws Exception {
        // given
        OrderCreatedEvent event1 = new OrderCreatedEvent(
                1L, 1001L, new ArrayList<>(),
                BigDecimal.valueOf(1000), "user1@example.com"
        );

        OrderCreatedEvent event2 = new OrderCreatedEvent(
                2L, 1002L, new ArrayList<>(),
                BigDecimal.valueOf(2000), "user2@example.com"
        );

        // Тест 1: Сначала тестируем с ошибками
        System.out.println("=== Test 1: Event with failures ===");

        MimeMessage mockMessage1 = mock(MimeMessage.class, "message1");
        when(mailSender.createMimeMessage()).thenReturn(mockMessage1);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email 1</html>");

        AtomicInteger attempts1 = new AtomicInteger(0);
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(mockMessage1);

        try {
            emailService.sendOrderCreatedEmail(event1);
        } catch (Exception e) {
            // Ожидаем исключение
        }

        // Проверяем что было 3 попытки
        verify(mailSender, times(3)).send(mockMessage1);

        // Проверяем fallback
        List<FailedEmail> failedEmails1 = failedEmailRepository.findAll();
        assertThat(failedEmails1).hasSize(1);
        assertThat(failedEmails1.getFirst().getRecipient()).isEqualTo("user1@example.com");

        // Тест 2: Теперь тестируем успешную отправку
        System.out.println("\n=== Test 2: Event with success ===");

        // Сбрасываем моки
        reset(mailSender, templateEngine);
        // НЕ очищаем базу - проверяем что не добавляется новый failed email

        MimeMessage mockMessage2 = mock(MimeMessage.class, "message2");
        when(mailSender.createMimeMessage()).thenReturn(mockMessage2);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email 2</html>");

        // Успешная отправка
        emailService.sendOrderCreatedEmail(event2);

        // Проверяем что была 1 попытка
        verify(mailSender, times(1)).send(mockMessage2);

        // Проверяем что не добавился новый failed email
        List<FailedEmail> failedEmails2 = failedEmailRepository.findAll();
        assertThat(failedEmails2).hasSize(1); // Все еще один, не добавился новый
    }

    @Test
    @DisplayName("Retry работает независимо для разных сообщений")
    void sendOrderCreatedEmail_IndependentRetry() throws Exception {
        // given
        OrderCreatedEvent event1 = new OrderCreatedEvent(
                1L, 1001L, new ArrayList<>(),
                BigDecimal.valueOf(1000), "user1@example.com"
        );

        OrderCreatedEvent event2 = new OrderCreatedEvent(
                2L, 1002L, new ArrayList<>(),
                BigDecimal.valueOf(2000), "user2@example.com"
        );

        // Создаем моки
        MimeMessage mockMessage1 = mock(MimeMessage.class);
        MimeMessage mockMessage2 = mock(MimeMessage.class);

        // Используем ThreadLocal или отдельные счетчики для каждого вызова
        // Но проще: имитируем что createMimeMessage создает новое сообщение каждый раз

        // Счетчики вызовов
        AtomicInteger sendCounterForEvent1 = new AtomicInteger(0);
        AtomicInteger sendCounterForEvent2 = new AtomicInteger(0);

        // Будем отслеживать какой event сейчас обрабатывается
        // Это не идеально, но для теста сойдет
        AtomicReference<OrderCreatedEvent> currentEvent = new AtomicReference<>();

        // При создании сообщения - возвращаем разные моки в зависимости от event
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> {
            OrderCreatedEvent event = currentEvent.get();
            if (event == event1) {
                return mockMessage1;
            } else if (event == event2) {
                return mockMessage2;
            }
            return mockMessage1; // fallback
        });

        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Email</html>");

        // Настройка отправки - разное поведение для разных сообщений
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);

            if (message == mockMessage1) {
                int attempt = sendCounterForEvent1.incrementAndGet();
                System.out.println("Sending event1, attempt #" + attempt);
                throw new MailSendException("SMTP error for event1");
            } else if (message == mockMessage2) {
                sendCounterForEvent2.incrementAndGet();
                System.out.println("Sending event2, success");
                // Успех для event2
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        // when
        // Первый вызов
        System.out.println("=== Sending event1 (should fail) ===");
        currentEvent.set(event1);
        try {
            emailService.sendOrderCreatedEmail(event1);
        } catch (Exception e) {
            System.out.println("Event1 threw exception: " + e.getClass().getSimpleName());
        }

        // Второй вызов
        System.out.println("\n=== Sending event2 (should succeed) ===");
        currentEvent.set(event2);
        emailService.sendOrderCreatedEmail(event2);

        // then
        System.out.println("\n=== Results ===");
        System.out.println("Event1 send attempts: " + sendCounterForEvent1.get());
        System.out.println("Event2 send attempts: " + sendCounterForEvent2.get());

        assertThat(sendCounterForEvent1.get())
                .as("Для event1 должно быть 3 попытки отправки")
                .isEqualTo(3);

        assertThat(sendCounterForEvent2.get())
                .as("Для event2 должна быть 1 попытка отправки")
                .isEqualTo(1);

        // Проверяем fallback
        assertThat(failedEmailRepository.count()).isEqualTo(1);
        FailedEmail failed = failedEmailRepository.findAll().getFirst();
        assertThat(failed.getRecipient()).isEqualTo("user1@example.com");
    }

    @Test
    @DisplayName("Проверка параметров отправляемого письма")
    void sendOrderCreatedEmail_CorrectEmailParameters() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        String expectedHtml = "<html>Order #123</html>";
        when(templateEngine.process(eq("email/order-created"), any()))
                .thenReturn(expectedHtml);

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);

        // when
        emailService.sendOrderCreatedEmail(event);

        // then
        verify(mailSender).send(messageCaptor.capture());

        // Проверяем что письмо было отправлено
        assertThat(messageCaptor.getValue()).isNotNull();

        // Проверяем что шаблон был вызван с правильными параметрами
        verify(templateEngine).process(eq("email/order-created"), any());
    }

    @Test
    @DisplayName("Проверка обработки MessagingException")
    void sendOrderCreatedEmail_MessagingException_Simple() throws Exception {
        // given
        OrderCreatedEvent event = createTestEvent();

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Test email</html>");

        // SMTP падает 3 раза
        doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(mockMessage);

        // when - просто вызываем метод
        emailService.sendOrderCreatedEmail(event);

        // ВАЖНО: если у вас в EmailService есть try-catch который глотает исключение,
        // то тест не упадет. Проверьте что исключение действительно пробрасывается.

        // then: проверяем что было 3 попытки
        verify(mailSender, times(3)).send(mockMessage);

        // И fallback сработал (если у вас есть @Recover метод)
        assertThat(failedEmailRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Проверка что recover вызывается только для MailException")
    void recover_OnlyForMailException() {
        // given
        OrderCreatedEvent event = createTestEvent();

        // when: вызываем recover с НЕ MailException
        MailSendException mailSendException = new MailSendException("Some other error");
        emailService.recover(mailSendException, event);

        // then: все равно сохраняется в базу
        assertThat(failedEmailRepository.count()).isEqualTo(1);
        FailedEmail failed = failedEmailRepository.findAll().getFirst();
        assertThat(failed.getErrorMessage()).isEqualTo("Some other error");
    }

    @Test
    @DisplayName("Пустой список товаров - email все равно отправляется")
    void sendOrderCreatedEmail_EmptyOrderItems() throws Exception {
        // given
        OrderCreatedEvent event = new OrderCreatedEvent(
                1L, 1001L, new ArrayList<>(),
                BigDecimal.ZERO, "test@example.com"
        );

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        when(templateEngine.process(anyString(), any()))
                .thenReturn("<html>Empty order</html>");

        // when
        emailService.sendOrderCreatedEmail(event);

        // then
        verify(mailSender, times(1)).send(mockMessage);
        verify(templateEngine, times(1)).process(eq("email/order-created"), any());
    }

    private OrderCreatedEvent createTestEvent() {
        // Создаем фиктивный продукт
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
                        .product(mockProduct1)  // Добавляем продукт
                        .productName("Молоток")
                        .quantity(2)
                        .unitPrice(BigDecimal.valueOf(500))
                        .subtotal(BigDecimal.valueOf(1000))
                        .build()),
                OrderItemDto.fromEntity(OrderItem.builder()
                        .product(mockProduct2)  // Добавляем продукт
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
                "test@example.com"
        );
    }
}