package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotAcceptableException;

import ru.fisher.ToolsMarket.exceptions.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import ru.fisher.ToolsMarket.PostgresTestConfig;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Обработчики ошибок должны согласовывать содержимое с запросом: браузер получает
 * HTML-страницу, API и fetch() — JSON. Раньше JSON отдавался всегда, и запрос с
 * Accept: text/html ронял сам обработчик с HttpMediaTypeNotAcceptableException,
 * из-за чего 404 превращался в 500.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ErrorContentNegotiationTest {

    private static final String BROWSER_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void browserGetsHtmlPageForMissingCategory() throws Exception {
        mockMvc.perform(get("/category/no-such-category")
                        .accept(BROWSER_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void browserGetsHtmlPageForMissingProduct() throws Exception {
        mockMvc.perform(get("/product/no-such-product")
                        .accept(BROWSER_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void browserGetsErrorCodeInModel() throws Exception {
        mockMvc.perform(get("/category/no-such-category")
                        .accept(BROWSER_HTML))
                .andExpect(status().isNotFound())
                .andExpect(model().attribute("errorCode", 404))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void jsonClientGetsJsonBody() throws Exception {
        mockMvc.perform(get("/category/no-such-category")
                        .accept("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/category/no-such-category"))
                .andExpect(jsonPath("$.error").value(containsString("Страница не найдена")));
    }

    @Test
    void requestWithoutAcceptHeaderGetsJson() throws Exception {
        // так ходят все fetch() в проекте: Accept не переопределяется, приходит */*
        mockMvc.perform(get("/category/no-such-category")
                        .accept("*/*"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void xhrRequestGetsJsonEvenWithHtmlInAccept() throws Exception {
        mockMvc.perform(get("/category/no-such-category")
                        .accept(BROWSER_HTML)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void nonNegotiableAcceptGetsStatusWithoutBody() throws Exception {
        // Клиент не принимает ни HTML, ни JSON: отдаём только статус, без попытки
        // отрендерить страницу (это и роняло обработчик с 406)
        mockMvc.perform(get("/category/no-such-category")
                        .accept("application/xml"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void mediaTypeNotAcceptableHandlerAnswers406WithDetails() {
        // Обработчик нужен для случая, когда 406 приходит как основное исключение
        // (например, API-контроллер отдаёт JSON клиенту, который его не принимает).
        // Через MockMvc такой Accept собрать нельзя, поэтому проверяем обработчик напрямую.
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        jakarta.servlet.http.HttpServletRequest request =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getMethod()).thenReturn("GET");
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/cart/state");
        org.mockito.Mockito.when(request.getHeader("Accept")).thenReturn("application/xml");

        var response = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(
                        "All known MediaTypes are not acceptable for \"/api/cart/state\""),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).containsEntry("path", "/api/cart/state")
                .containsEntry("error", "Неприемлемый тип ответа");
    }

    @Test
    void apiPathAlwaysGetsJson() throws Exception {
        mockMvc.perform(get("/api/cart/state")
                        .accept(BROWSER_HTML))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
