package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import ru.fisher.ToolsMarket.PostgresTestConfig;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Анти-сканер: POST на GET-only страницу должен давать тихий 405, а не 500
 * из общего @ExceptionHandler(Exception.class).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class MethodNotAllowedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postToGetOnlyHomePageReturns405() throws Exception {
        mockMvc.perform(post("/"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(view().name("error/405"));
    }

    @Test
    void postToGetOnlyCategoryPageReturns405() throws Exception {
        mockMvc.perform(post("/search").param("query", "drill"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownRootPathsReturn404() throws Exception {
        // Сканеры форм входа долбят /login, /register, /auth — контроллеров там нет
        mockMvc.perform(get("/login")).andExpect(status().isNotFound());
        mockMvc.perform(get("/register")).andExpect(status().isNotFound());
        mockMvc.perform(get("/auth")).andExpect(status().isNotFound());
    }

    @Test
    void realAuthPagesAreNotBlocked() throws Exception {
        mockMvc.perform(get("/auth/login").with(csrf())).andExpect(status().isOk());
    }
}
