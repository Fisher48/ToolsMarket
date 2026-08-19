package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke-рендер всех шаблонов через реальные сервисы и реальную БД.
 * Цель — поймать ошибки Thymeleaf/рендера, а также убедиться, что страницы
 * подключают site.css / admin-sidebar.css.
 *
 * При addFilters=false контроллеры, принимающие {@code Authentication} / {@code Principal},
 * резолвят их из request.getUserPrincipal(), который здесь выставляется через auth().
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class TemplateRenderTest {

    private static final String USERNAME = "testuser";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private Long productId;
    private Long categoryId;
    private Long orderId;
    private String productTitle;
    private String categoryTitle;

    @BeforeEach
    void seed() {
        SecurityContextHolder.clearContext();

        User user = userService.createAdminUser(USERNAME, "testuser@example.com", "password");
        userId = user.getId();

        Category category = Category.builder()
                .title("teplovye-pushki")
                .name("Тепловые пушки")
                .description("Тепловое оборудование")
                .sortOrder(0)
                .createdAt(Instant.now())
                .build();
        categoryTitle = categoryService.saveEntity(category).getTitle();
        categoryId = category.getId();

        Product product = Product.builder()
                .name("Тепловая пушка 3 кВт")
                .title("teplovaya-pushka-3-kvt")
                .sku("SKU-TP3")
                .price(new BigDecimal("4500.00"))
                .currency("RUB")
                .shortDescription("Компактная тепловая пушка")
                .description("Описание тепловой пушки")
                .active(true)
                .views(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .images(new LinkedHashSet<>())
                .categories(new HashSet<>(Set.of(category)))
                .attributeValues(new LinkedHashSet<>())
                .build();
        productTitle = productService.saveEntity(product).getTitle();
        productId = product.getId();

        Cart cart = cartService.getOrCreateCart(userId);
        cartService.addProductWithQuantity(cart.getId(), productId, 2);

        Order order = orderService.createOrderFromUserCart(userId, "");
        orderId = order.getId();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE TABLE users, \"order\", cart, product, category, "
                + "user_discounts, failed_emails RESTART IDENTITY CASCADE");
    }

    @Test
    void catalogPagesRenderWithSiteCss() throws Exception {
        assertPublicRenders("/");
        assertPublicRenders("/search?q=" + encode("Тепловая"));
        assertPublicRenders("/category/" + categoryTitle);
        assertPublicRenders("/product/" + productTitle);
        assertPublicRenders("/cart");
    }

    @Test
    void orderPagesRenderWithSiteCss() throws Exception {
        assertPublicRenders("/order/checkout");
        assertPublicRenders("/order/" + orderId);
    }

    @Test
    void profilePagesRenderWithSiteCss() throws Exception {
        assertPublicRenders("/profile");
        assertPublicRenders("/profile/edit");
        assertPublicRenders("/profile/orders");
        assertPublicRenders("/profile/orders/" + orderId);
    }

    @Test
    void authAndErrorPagesRenderWithSiteCss() throws Exception {
        assertRenders("/auth/login", "/css/site.css");
        assertRenders("/auth/register", "/css/site.css");
        assertRenders("/auth/access-denied", "/css/site.css");
        assertRenders("/error/403", "/css/site.css");
        assertRenders("/error/404", "/css/site.css");
        assertRenders("/error/500", "/css/site.css");
        assertRenders("/error", "/css/site.css");
    }

    @Test
    void adminPagesRenderWithSidebarCss() throws Exception {
        assertAdminRenders("/admin/users");
        assertAdminRenders("/admin/users/" + userId);
        assertAdminRenders("/admin/users/" + userId + "/edit");
        assertAdminRenders("/admin/users/" + userId + "/change-password");
        assertAdminRenders("/admin/products");
        assertAdminRenders("/admin/products/" + productId);
        assertAdminRenders("/admin/products/" + productId + "/edit");
        assertAdminRenders("/admin/categories");
        assertAdminRenders("/admin/categories/" + categoryId);
        assertAdminRenders("/admin/categories/new");
        assertAdminRenders("/admin/categories/" + categoryId + "/edit");
        assertAdminRenders("/admin/orders");
        assertAdminRenders("/admin/orders/" + orderId);
        assertAdminRenders("/admin/discounts");
        assertAdminRenders("/admin/prices");
        assertAdminRenders("/admin/excel-import");
        assertAdminRenders("/admin/parser");
    }

    private void assertPublicRenders(String url) throws Exception {
        assertRenders(url, "/css/site.css", "USER");
    }

    private void assertAdminRenders(String url) throws Exception {
        mockMvc.perform(get(url).with(csrf()).with(auth("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/admin-sidebar.css")));
    }

    private void assertRenders(String url, String cssMarker) throws Exception {
        assertRenders(url, cssMarker, new String[0]);
    }

    private void assertRenders(String url, String cssMarker, String... roles) throws Exception {
        mockMvc.perform(get(url).with(csrf()).with(auth(roles)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(cssMarker)));
    }

    /**
     * Кладёт аутентификацию и в SecurityContextHolder (для @AuthenticationPrincipal,
     * CartController, method security), и в request.getUserPrincipal() (для
     * параметров Authentication/Principal).
     */
    private RequestPostProcessor auth(String... roles) {
        return request -> {
            UserDetails details = org.springframework.security.core.userdetails.User
                    .withUsername(USERNAME)
                    .password("password")
                    .roles(roles)
                    .build();
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(token);
            request.setUserPrincipal(token);
            return request;
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
