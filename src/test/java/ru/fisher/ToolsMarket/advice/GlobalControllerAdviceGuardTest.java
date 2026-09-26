package ru.fisher.ToolsMarket.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверяем, что loginRedirectUrl пишется в сессию только при изменении URL:
 * повторные запросы с тем же URL не должны порождать delta-записи в
 * SPRING_SESSION_ATTRIBUTES. Тест не поднимает Spring-контекст (Docker не нужен).
 */
class GlobalControllerAdviceGuardTest {

    private static final String HUMAN_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final GlobalControllerAdvice advice = new GlobalControllerAdvice();

    private HttpServletRequest request(String uri, String query, String userAgent) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getQueryString()).thenReturn(query);
        when(req.getHeader("User-Agent")).thenReturn(userAgent);
        return req;
    }

    private HttpSession sessionWithStoredValue(AtomicReference<String> stored) {
        HttpSession session = mock(HttpSession.class);
        doAnswer(inv -> {
            stored.set(inv.getArgument(1));
            return null;
        }).when(session).setAttribute(eq("loginRedirectUrl"), any());
        when(session.getAttribute("loginRedirectUrl")).thenAnswer(inv -> stored.get());
        return session;
    }

    @Test
    void writesOnlyWhenUrlChanged() {
        AtomicReference<String> stored = new AtomicReference<>();
        HttpSession session = sessionWithStoredValue(stored);
        Model model = mock(Model.class);

        // 1-й запрос: атрибута ещё нет -> setAttribute вызывается
        HttpServletRequest first = request("/category/bolt", "page=2", HUMAN_UA);
        when(first.getSession(false)).thenReturn(session);
        advice.addCurrentUrl(first, model);
        verify(session, times(1)).setAttribute("loginRedirectUrl", "/category/bolt?page=2");
        assertEquals("/category/bolt?page=2", stored.get());

        // 2-й запрос с тем же URL -> setAttribute НЕ вызывается
        HttpServletRequest second = request("/category/bolt", "page=2", HUMAN_UA);
        when(second.getSession(false)).thenReturn(session);
        advice.addCurrentUrl(second, model);
        verify(session, times(1)).setAttribute("loginRedirectUrl", "/category/bolt?page=2");

        // 3-й запрос с другим URL -> setAttribute вызывается снова
        HttpServletRequest third = request("/product/17", null, HUMAN_UA);
        when(third.getSession(false)).thenReturn(session);
        advice.addCurrentUrl(third, model);
        verify(session, times(1)).setAttribute("loginRedirectUrl", "/product/17");
        // Всего два вызова setAttribute за три запроса
        verify(session, times(2)).setAttribute(eq("loginRedirectUrl"), any());
        assertEquals("/product/17", stored.get());
    }

    @Test
    void doesNotTouchSessionWithoutExistingOne() {
        HttpServletRequest req = request("/category/bolt", null, HUMAN_UA);
        when(req.getSession(false)).thenReturn(null);
        Model model = mock(Model.class);

        advice.addCurrentUrl(req, model);

        verify(req, never()).getSession();
        verify(model).addAttribute("currentUrl", "/category/bolt");
    }

    @Test
    void skipsBotUserAgent() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenReturn(null);
        HttpServletRequest req = request("/category/bolt", null, "Googlebot/2.1");
        when(req.getSession(false)).thenReturn(session);

        advice.addCurrentUrl(req, mock(Model.class));

        verify(session, never()).setAttribute(eq("loginRedirectUrl"), any());
    }

    @Test
    void skipsTechnicalPaths() {
        for (String path : new String[]{"/api/x", "/auth/login", "/error", "/actuator/health",
                "/images/photo.jpg", "/css/main.css", "/js/app.js", "/static/x", "/webjars/x"}) {
            AtomicReference<String> stored = new AtomicReference<>();
            HttpSession session = sessionWithStoredValue(stored);
            HttpServletRequest req = request(path, null, HUMAN_UA);
            when(req.getSession(false)).thenReturn(session);

            advice.addCurrentUrl(req, mock(Model.class));

            verify(session, never()).setAttribute(eq("loginRedirectUrl"), any());
        }
    }
}
