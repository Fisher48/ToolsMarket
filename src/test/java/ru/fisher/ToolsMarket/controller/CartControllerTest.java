package ru.fisher.ToolsMarket.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;


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
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    private User mockUser() {
        return User.builder().id(1L).username("testuser").build();
    }

    private void stubAuthenticatedUser() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(mockUser()));
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewCartWithAuthenticatedUser() throws Exception {
        // given - аутентифицированный пользователь
        Cart cart = new Cart();
        cart.setId(1L);

        stubAuthenticatedUser();
        when(cartService.getOrCreateCart(1L)).thenReturn(cart);
        when(cartService.getUserCartItems(1L)).thenReturn(List.of());
        when(cartService.calculateSummary(any())).thenReturn(BigDecimal.ZERO);

        // when & then
        mockMvc.perform(get("/cart").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("cart/index"))
                .andExpect(model().attributeExists("cart", "items", "currentUser"))
                .andExpect(model().attribute("totalItemCount", 0));

        verify(cartService).getOrCreateCart(1L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewCartWithoutAuthenticationRedirectsToLogin() throws Exception {
        // given - пользователь не найден (гость)
        when(userService.findByUsername("testuser")).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/cart").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void viewCartAsAnonymousUserRedirectsToLogin() throws Exception {
        // when & then - анонимный пользователь
        mockMvc.perform(get("/cart").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithAuthenticatedUser() throws Exception {
        // given - аутентифицированный пользователь
        stubAuthenticatedUser();
        doNothing().when(cartService).addProductToUserCart(1L, 123L, 1);

        // when & then
        mockMvc.perform(post("/cart/add").with(csrf())
                        .param("productId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductToUserCart(1L, 123L, 1);
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithQuantityAddsMultipleItems() throws Exception {
        // given
        stubAuthenticatedUser();
        doNothing().when(cartService).addProductToUserCart(1L, 1L, 3);

        // when & then
        mockMvc.perform(post("/cart/add").with(csrf())
                        .param("productId", "1")
                        .param("quantity", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductToUserCart(1L, 1L, 3);
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartWithDefaultQuantityAddsOneItem() throws Exception {
        // given - quantity не передаем, должен быть дефолтный 1
        stubAuthenticatedUser();
        doNothing().when(cartService).addProductToUserCart(1L, 1L, 1);

        // when & then
        mockMvc.perform(post("/cart/add").with(csrf())
                        .param("productId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addProductToUserCart(1L, 1L, 1);
    }

    @Test
    @WithMockUser(username = "testuser")
    void addToCartRedirectsToLoginWhenUserNotFound() throws Exception {
        // given - пользователь не найден
        when(userService.findByUsername("testuser")).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(post("/cart/add").with(csrf())
                        .param("productId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        verify(cartService, org.mockito.Mockito.never()).addProductToUserCart(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @WithMockUser(username = "testuser")
    void removeFromCart() throws Exception {
        // given
        stubAuthenticatedUser();
        doNothing().when(cartService).removeProductFromUserCart(1L, 123L);

        // when & then
        mockMvc.perform(post("/cart/remove").with(csrf())
                        .param("productId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).removeProductFromUserCart(1L, 123L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void decreaseQuantity() throws Exception {
        // given
        stubAuthenticatedUser();
        doNothing().when(cartService).decreaseProductInUserCart(1L, 123L);

        // when & then
        mockMvc.perform(post("/cart/decrease").with(csrf())
                        .param("productId", "123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).decreaseProductInUserCart(1L, 123L);
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearCart() throws Exception {
        // given
        stubAuthenticatedUser();
        doNothing().when(cartService).clearUserCart(1L);

        // when & then
        mockMvc.perform(post("/cart/clear").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).clearUserCart(1L);
    }
}
