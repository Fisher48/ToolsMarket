package ru.fisher.ToolsMarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

/**
 * Настройки хранилища сессий Spring Session JDBC.
 */
@Configuration
public class SessionConfig {

    /**
     * PostgreSQL-специфичный SQL для атрибутов сессии: INSERT ... ON CONFLICT DO UPDATE.
     * Без этого два параллельных запроса одной сессии (например две вкладки на странице
     * категории) вставляют один и тот же атрибут простым INSERT, и второй падает на
     * SPRING_SESSION_ATTRIBUTES_PK с DuplicateKeyException — страница отдаёт 500.
     * Spring Boot подключает диалектный customizer только если он зарегистрирован бином.
     */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> postgresSessionRepositoryCustomizer() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}
