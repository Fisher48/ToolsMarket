package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Номер заказа должен быть уникальным за счёт последовательности в базе, а не
 * за счёт случайных цифр: раньше два заказа одного пользователя в пределах
 * одной минуты совпадали с вероятностью 1%, и клиент получал 500.
 */
@ExtendWith(MockitoExtension.class)
class OrderNumberGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    /** 2026-09-26 14:30 по Москве */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-26T11:30:00Z"), ZONE);

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OrderNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OrderNumberGenerator(jdbcTemplate, FIXED_CLOCK);
    }

    private void sequenceReturns(long... values) {
        AtomicLong current = new AtomicLong();
        List<Long> queue = new ArrayList<>();
        for (long value : values) {
            queue.add(value);
        }
        when(jdbcTemplate.queryForObject(eq("SELECT nextval('order_number_seq')"), eq(Long.class)))
                .thenAnswer(inv -> queue.isEmpty() ? current.incrementAndGet() : queue.remove(0));
    }

    @Test
    void numberConsistsOfDateUserAndSequence() {
        sequenceReturns(1);

        // 2609261430 (дата) + 0042 (userId) + 0001 (последовательность)
        assertThat(generator.next(42L)).isEqualTo(260926143000420001L);
    }

    @Test
    void consecutiveCallsInSameMinuteDiffer() {
        sequenceReturns(1, 2, 3, 4, 5);

        List<Long> numbers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            numbers.add(generator.next(42L));
        }

        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(number ->
                assertThat(String.valueOf(number)).startsWith("2609261430"));
    }

    @Test
    void manyOrdersOfSameUserInSameMinuteAreUnique() {
        // Сценарий, который раньше падал: 25 заказов одного пользователя
        AtomicLong current = new AtomicLong();
        when(jdbcTemplate.queryForObject(eq("SELECT nextval('order_number_seq')"), eq(Long.class)))
                .thenAnswer(inv -> current.incrementAndGet());

        List<Long> numbers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            numbers.add(generator.next(7L));
        }

        assertThat(numbers).doesNotHaveDuplicates();
    }

    @Test
    void sequenceIsTakenFromDatabase() {
        sequenceReturns(12345);

        generator.next(1L);

        org.mockito.Mockito.verify(jdbcTemplate)
                .queryForObject("SELECT nextval('order_number_seq')", Long.class);
    }

    @Test
    void userIdAboveModuloKeepsLastFourDigits() {
        sequenceReturns(1, 2);

        // 10005 и 5 дают одинаковую часть с идентификатором: уникальность
        // обеспечивает последовательность, а не userId
        assertThat(String.valueOf(generator.next(10_005L)).substring(10, 14)).isEqualTo("0005");
        assertThat(String.valueOf(generator.next(5L)).substring(10, 14)).isEqualTo("0005");
    }

    @Test
    void numberStaysInsideLongRange() {
        sequenceReturns(Long.MAX_VALUE);

        Long number = generator.next(99_999L);

        assertThat(number).isPositive();
        assertThat(String.valueOf(number)).hasSize(18);
    }

    @Test
    void missingSequenceFailsWithClearMessage() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThatThrownBy(() -> generator.next(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order_number_seq");
    }
}
