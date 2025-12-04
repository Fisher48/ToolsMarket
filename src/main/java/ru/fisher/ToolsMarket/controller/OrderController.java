package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.OrderService;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;

    @GetMapping("/{orderId}")
    public String viewOrder(@PathVariable Long orderId, Model model) {
        try {
            Order order = orderService.getOrder(orderId);
            model.addAttribute("order", order);
            return "order/index";
        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден: id={}", orderId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден");
        } catch (Exception e) {
            log.error("Ошибка при получении заказа: id={}", orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка сервера");
        }
    }

    @GetMapping("/checkout")
    public String checkout(@CookieValue(value = "sessionId") String sessionId, Model model) {
        try {
            Cart cart = cartService.getOrCreateCart(null, sessionId);
            model.addAttribute("cart", cart);
            model.addAttribute("items", cartService.getCartItems(cart.getId()));
            return "order/checkout";
        } catch (Exception e) {
            log.error("Ошибка при оформлении заказа: sessionId={}", sessionId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка сервера");
        }
    }

    @PostMapping("/create")
    public String createOrder(@CookieValue(value = "sessionId") String sessionId,
                              RedirectAttributes redirectAttributes) {
        try {
            Cart cart = cartService.getOrCreateCart(null, sessionId);
            Order order = orderService.createOrder(cart.getId());

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Заказ №" + order.getOrderNumber() + " успешно создан!"
            );
            return "redirect:/order/" + order.getId();

        } catch (IllegalStateException e) {
            // Обрабатываем "Cart is empty"
            log.warn("Попытка создать заказ из пустой корзины: sessionId={}", sessionId);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Невозможно создать заказ из пустой корзины"
            );
            return "redirect:/cart";
        } catch (IllegalArgumentException e) {
            // Обрабатываем "Cart not found"
            log.warn("Корзина не найдена: sessionId={}", sessionId, e);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Корзина не найдена"
            );
            return "redirect:/cart";
        } catch (Exception e) {
            log.error("Ошибка при создании заказа: sessionId={}", sessionId, e);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Произошла ошибка при создании заказа"
            );
            return "redirect:/cart";
        }
    }

    @PostMapping("/{orderId}/cancel")
    public String cancelOrder(@PathVariable Long orderId,
                              RedirectAttributes redirectAttributes) {
        try {
            orderService.updateStatus(orderId, OrderStatus.CANCELLED);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Заказ отменен"
            );

        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден при отмене: id={}", orderId);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Заказ не найден"
            );
        } catch (OrderFinalizedException e) {
            log.warn("Попытка отменить завершенный заказ: id={}", orderId);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    e.getMessage()
            );
        } catch (Exception e) {
            log.error("Ошибка при отмене заказа: id={}", orderId, e);
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Произошла ошибка при отмене заказа"
            );
        }

        return "redirect:/order/" + orderId;
    }

}
