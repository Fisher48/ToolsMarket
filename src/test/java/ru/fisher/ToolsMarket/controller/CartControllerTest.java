package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
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
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Slf4j
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CartControllerTest {

    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private UserService userService; // Добавляем UserService

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser")
    void viewCartCreatesNewSessionIdWhenNotExists() throws Exception {
        // given - нет куки sessionId
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(any(), any())).thenReturn(cart);
        when(cartService.getCartItems(any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("sessionId"))
                .andExpect(view().name("cart/index"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewCartWithAuthenticatedUser() throws Exception {
        // given - аутентифицированный пользователь
        Cart cart = new Cart();
        cart.setId(1L);

        // Мокаем userService для получения userId
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));
        when(cartService.getOrCreateCart(eq(1L), any())).thenReturn(cart);
        when(cartService.getCartItems(1L)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/cart").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("cart/index"))
                .andExpect(model().attributeExists("isAuthenticated"))
                .andExpect(model().attribute("isAuthenticated", true));

        verify(cartService).getOrCreateCart(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewCartWithoutAuthentication() throws Exception {
        // given - неаутентифицированный пользователь
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), any())).thenReturn(cart);
        when(cartService.getCartItems(1L)).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(view().name("cart/index"))
                .andExpect(model().attributeExists("isAuthenticated"))
                .andExpect(model().attribute("isAuthenticated", false));

        verify(cartService).getOrCreateCart(isNull(), any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void addToCartCreatesProductInCartForUnauthenticatedUser() throws Exception {
        // given - неаутентифицированный пользователь
        String sessionId = "existing-session";
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add")
                        .param("productId", "123")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 123L, 1);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void addToCartWithAuthenticatedUser() throws Exception {
        // given - аутентифицированный пользователь
        String sessionId = "existing-session";
        Cart cart = new Cart();
        cart.setId(1L);

        // Мокаем userService
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));
        when(cartService.getOrCreateCart(eq(1L), eq(sessionId))).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add").with(csrf())
                        .param("productId", "123")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 123L, 1);
        verify(cartService).getOrCreateCart(eq(1L), eq(sessionId));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithQuantityAddsMultipleItems() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 1L;
        int quantity = 3;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add")
                        .param("productId", productId.toString())
                        .param("quantity", String.valueOf(quantity))
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 1L, 3);
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithDefaultQuantityAddsOneItem() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 1L;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);

        // when & then - quantity не передаем, должен быть дефолтный 1
        mockMvc.perform(post("/cart/add")
                        .param("productId", productId.toString())
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 1L, 1);
    }

    @Test
    @WithMockUser(username = "testuser")
    void removeFromCart() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 123L;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);
        doNothing().when(cartService).removeProduct(1L, 123L);

        // when & then
        mockMvc.perform(post("/cart/remove")
                        .param("productId", productId.toString())
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).removeProduct(1L, 123L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void decreaseQuantity() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 123L;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);
        doNothing().when(cartService).decreaseProductQuantity(1L, 123L);

        // when & then
        mockMvc.perform(post("/cart/decrease")
                        .param("productId", productId.toString())
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).decreaseProductQuantity(1L, 123L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void mergeCartSuccess() throws Exception {
        // given
        String sessionId = "test-session";

        // Мокаем userService
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));

        Cart mergedCart = new Cart();
        mergedCart.setId(2L);

        when(cartService.mergeCartToUser(sessionId, 1L)).thenReturn(mergedCart);

        // when & then
        mockMvc.perform(post("/cart/merge")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).mergeCartToUser(sessionId, 1L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void mergeCartWithoutAuthentication() throws Exception {
        // given - неаутентифицированный пользователь
        String sessionId = "test-session";

        // when & then
        mockMvc.perform(post("/cart/merge")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("error"));

        verify(cartService, never()).mergeCartToUser(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearCart() throws Exception {
        // given
        String sessionId = "test-session";
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), eq(sessionId))).thenReturn(cart);
        doNothing().when(cartService).clearCart(1L);

        // when & then
        mockMvc.perform(post("/cart/clear")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).clearCart(1L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithoutSessionIdCreatesNewSession() throws Exception {
        // given - нет куки sessionId
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(isNull(), any())).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add")
                        .param("productId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(cookie().exists("sessionId"));

        verify(cartService).addProductWithQuantity(1L, 123L, 1);
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewCartWithAuthenticatedUserAndAnonymousCart() throws Exception {
        // given - аутентифицированный пользователь с sessionId
        String sessionId = "existing-session";
        Cart cart = new Cart();
        cart.setId(1L);

        // Важно: userId и sessionId оба передаются в getOrCreateCart
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));
        when(cartService.getOrCreateCart(eq(1L), eq(sessionId))).thenReturn(cart);
        when(cartService.getCartItems(1L)).thenReturn(List.of());

        // when & then
        MvcResult result = mockMvc.perform(get("/cart").with(csrf())
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().isOk())
                .andExpect(view().name("cart/index"))
                .andReturn();

        // Проверяем сессионные атрибуты
        HttpSession session = result.getRequest().getSession();

        // hasAnonymousCart будет true только если:
        // 1. userId != null (аутентифицирован)
        // 2. finalSessionId != null и не пустой
        // В тесте finalSessionId берется из куки "sessionId"

        Assertions.assertNotNull(session);
        Boolean hasAnonymousCart = (Boolean) session.getAttribute("hasAnonymousCart");
        String anonymousSessionId = (String) session.getAttribute("anonymousSessionId");

        // Для отладки
        log.info("hasAnonymousCart: {}", hasAnonymousCart);
        log.info("anonymousSessionId: {}", anonymousSessionId);
        log.info("Session ID from cookie: {}", sessionId);
    }
}
