//package ru.fisher.ToolsMarket.util;
//
//import org.springframework.web.bind.annotation.ControllerAdvice;
//import org.springframework.web.bind.annotation.CookieValue;
//import org.springframework.web.bind.annotation.ModelAttribute;
//import ru.fisher.ToolsMarket.models.Cart;
//import ru.fisher.ToolsMarket.service.CartService;
//
//@ControllerAdvice
//public class CartComponent {
//
//    private final CartService cartService;
//
//    public CartComponent(CartService cartService) {
//        this.cartService = cartService;
//    }
//
//    @ModelAttribute("cartItemCount")
//    public int getCartItemCount(@CookieValue(value = "sessionId", required = false)
//                                String sessionId) {
//        if (sessionId == null) {
//            return 0;
//        }
//        try {
//            Cart cart = cartService.getOrCreateCart(null, sessionId);
//            return cartService.getCartItems(cart.getId()).size();
//        } catch (Exception e) {
//            return 0;
//        }
//    }
//}
