package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CartControllerTest {

    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private ProductService productService;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void viewCartCreatesNewSessionIdWhenNotExists() throws Exception {
        // given - нет куки sessionId
        when(cartService.getOrCreateCart(any(), any())).thenReturn(new Cart());
        when(cartService.getCartItems(any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("sessionId"))
                .andExpect(view().name("cart/index"));
    }

    @Test
    void addToCartCreatesProductInCart() throws Exception {
        // given
        String sessionId = "existing-session";
        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add")
                        .param("productId", "123")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 123L, 1);
    }

    @Test
    void addToCartWithQuantityAddsMultipleItems() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 1L;
        int quantity = 3;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);

        // when & then
        mockMvc.perform(post("/cart/add")
                        .param("productId", productId.toString())
                        .param("quantity", String.valueOf(quantity))
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        // Проверяем что вызвался метод с правильным quantity
        verify(cartService).addProductWithQuantity(1L, 1L, 3);
    }

    @Test
    void addToCartWithDefaultQuantityAddsOneItem() throws Exception {
        // given
        String sessionId = "test-session";
        Long productId = 1L;

        Cart cart = new Cart();
        cart.setId(1L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);

        // when & then - quantity не передаем, должен быть дефолтный 1
        mockMvc.perform(post("/cart/add")
                        .param("productId", productId.toString())
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductWithQuantity(1L, 1L, 1);
    }

    @Test
    void viewCartCreatesSessionIdWhenNotExists() throws Exception {
        // Given - нет куки sessionId
        when(cartService.getOrCreateCart(any(), any())).thenReturn(new Cart());
        when(cartService.getCartItems(any())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("sessionId"))
                .andExpect(view().name("cart/index"));
    }

}
