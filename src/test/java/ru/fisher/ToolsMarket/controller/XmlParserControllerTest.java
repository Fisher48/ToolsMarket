package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.parsingXml.StemYmlImportService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тест, что YML-импорт записывает "кто делал изменения": контроллер резолвит
 * текущего пользователя в потоке запроса и передаёт его id в импорт-сервис
 * (SecurityContext не пробрасывается в воркер-поток taskExecutor).
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class XmlParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StemYmlImportService ymlImportService;

    @MockitoBean
    private UserService userService;

    private void stubAdminUser() {
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void runImport_passesCurrentUserId_toImportService() throws Exception {
        stubAdminUser();
        when(ymlImportService.importFromUrl(anyString(), eq(1L)))
                .thenReturn(new StemYmlImportService.ImportResult(
                        true, 2, 2, 1, 1, List.of(), false, null));

        MvcResult mvcResult = mockMvc.perform(post("/admin/parser/run")
                        .param("xmlUrl", "https://example.com/feed.xml")
                        .with(csrf()))
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(ymlImportService).importFromUrl(eq("https://example.com/feed.xml"), eq(1L));
    }
}