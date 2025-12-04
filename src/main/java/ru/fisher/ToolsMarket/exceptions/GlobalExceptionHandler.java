package ru.fisher.ToolsMarket.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException e,
                                        RedirectAttributes redirectAttributes) {
        log.warn("Некорректный аргумент: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/admin/orders";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException e,
                                     RedirectAttributes redirectAttributes,
                                     HttpServletRequest request) {
        log.warn("Некорректное состояние: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());

        // Возвращаем на предыдущую страницу
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/admin/orders");
    }

//    @ExceptionHandler(Exception.class)
//    public String handleGeneralException(Exception e,
//                                         RedirectAttributes redirectAttributes) {
//        // Если это ResponseStatusException - пропускаем, Spring сам его обработает
//        if (e instanceof ResponseStatusException) {
//            throw (ResponseStatusException) e;
//        }
//        log.error("Неожиданная ошибка", e);
//        redirectAttributes.addFlashAttribute("errorMessage",
//                "Произошла внутренняя ошибка. Пожалуйста, попробуйте позже.");
//        return "redirect:/admin/orders";
//    }

    @ExceptionHandler(OrderNotFoundException.class)
    public String handleOrderNotFound(OrderNotFoundException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/cart"; // или куда нужно перенаправлять
    }

    @ExceptionHandler(OrderFinalizedException.class)
    public String handleOrderFinalized(OrderFinalizedException e,
                                       RedirectAttributes redirectAttributes,
                                       HttpServletRequest request) {
        log.warn("Попытка изменить завершенный заказ: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return getRedirectUrl(request, "/admin/orders");
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public String handleInvalidStatusTransition(InvalidStatusTransitionException e,
                                                RedirectAttributes redirectAttributes,
                                                HttpServletRequest request) {
        log.warn("Некорректный переход статуса: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return getRedirectUrl(request, "/admin/orders");
    }

    @ExceptionHandler(OrderValidationException.class)
    public String handleOrderValidation(OrderValidationException e,
                                        RedirectAttributes redirectAttributes,
                                        HttpServletRequest request) {
        log.warn("Ошибка валидации заказа: {}", e.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return getRedirectUrl(request, "/admin/orders");
    }

    @ExceptionHandler(OrderException.class)
    public String handleOrderException(OrderException e,
                                       RedirectAttributes redirectAttributes) {
        log.error("Ошибка в работе с заказами: {}", e.getMessage(), e);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Ошибка при обработке заказа: " + e.getMessage());
        return "redirect:/admin/orders";
    }

    private String getRedirectUrl(HttpServletRequest request, String defaultUrl) {
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : defaultUrl);
    }
}
