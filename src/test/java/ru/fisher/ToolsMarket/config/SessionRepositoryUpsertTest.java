package ru.fisher.ToolsMarket.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import ru.fisher.ToolsMarket.PostgresTestConfig;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Регрессия на DuplicateKeyException по SPRING_SESSION_ATTRIBUTES_PK.
 *
 * Проверяем, что (1) SessionConfig реально применил PostgreSQL-диалектный SQL
 * (INSERT ... ON CONFLICT DO UPDATE) к JdbcIndexedSessionRepository и (2) параллельные
 * сохранения одного и того же атрибута сессии не приводят к ошибке и не плодят дубли.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class SessionRepositoryUpsertTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanSessions() {
        jdbcTemplate.update("TRUNCATE spring_session_attributes, spring_session RESTART IDENTITY CASCADE");
    }

    /**
     * Новая сессия репозитория. JdbcSession — package-private класс с package-private
     * конструктором, поэтому создаём его рефлексией (id=null, isNew=true).
     */
    private Session newJdbcSession() throws Exception {
        Class<?> jdbcSessionClass = Class.forName(
                "org.springframework.session.jdbc.JdbcIndexedSessionRepository$JdbcSession");
        Constructor<?> constructor = jdbcSessionClass.getDeclaredConstructor(
                JdbcIndexedSessionRepository.class, MapSession.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return (Session) constructor.newInstance(sessionRepository, new MapSession(),
                UUID.randomUUID().toString(), true);
    }

    @Test
    void sessionAttributeQueryUsesOnConflictUpsert() {
        String query = (String) ReflectionTestUtils.getField(
                sessionRepository, "createSessionAttributeQuery");

        assertNotNull(query, "в репозитории нет SQL для вставки атрибутов сессии");
        assertTrue(query.toUpperCase().contains("ON CONFLICT"),
                "атрибуты сессии вставляются без ON CONFLICT, параллельные запросы дадут DuplicateKeyException: " + query);
    }

    @Test
    void concurrentSavesOfSameAttributeDoNotFail() throws Exception {
        // JdbcSession — package-private, поэтому создаём сессию рефлексией
        // (в 3.5.3 findById() для несуществующей сессии возвращает null)
        Session created = newJdbcSession();
        created.setAttribute("seed", "1");
        sessionRepository.save(created);
        String sessionId = created.getId();
        assertNotNull(sessionId, "сессия не сохранилась");
        // primary key сессии в SPRING_SESSION не совпадает с её id
        String primaryKey = (String) ReflectionTestUtils.getField(created, "primaryKey");

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String value = "value-" + i;
                tasks.add(() -> {
                    // Каждый поток читает сессию ДО записи — имитация двух вкладок:
                    // все считают атрибут новым относительно своего снимка
                    Session own = sessionRepository.findById(sessionId);
                    barrier.await(10, TimeUnit.SECONDS);
                    own.setAttribute("loginRedirectUrl", value);
                    sessionRepository.save(own);
                    return null;
                });
            }

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                // DuplicateKeyException здесь и был причиной 500 на страницах
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Map<String, Object> rows = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS cnt FROM spring_session_attributes"
                        + " WHERE session_primary_id = ? AND attribute_name = ?",
                primaryKey, "loginRedirectUrl");
        assertEquals(1L, ((Number) rows.get("cnt")).longValue(),
                "в SPRING_SESSION_ATTRIBUTES появились дубли одного атрибута");

        // Кто из потоков выиграл гонку — не важно, важно что значение одно из записанных
        String stored = (String) sessionRepository.findById(sessionId)
                .getAttribute("loginRedirectUrl");
        assertTrue(stored != null && stored.startsWith("value-"),
                "значение атрибута не соответствует ни одной из записей: " + stored);
    }
}
