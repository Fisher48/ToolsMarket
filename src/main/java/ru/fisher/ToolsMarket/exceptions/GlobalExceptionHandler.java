package ru.fisher.ToolsMarket.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    @ExceptionHandler(Exception.class)
    public String handleAllExceptions(Exception ex, Model model, HttpServletRequest request) {
        log.error("Необработанное исключение: ", ex);

        model.addAttribute("error", ex.getMessage());
        model.addAttribute("status", 500);
        model.addAttribute("path", request.getRequestURI());

        return "error/error"; // Указываем правильный путь к шаблону
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("status", 404);
        return "error/error";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("errors", errors);
        response.put("message", "Ошибка валидации");

        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        body.put("path", request.getRequestURI());

        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleMissingCookie(MissingRequestCookieException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", String.format("Required cookie '%s' is not present",
                ex.getCookieName()));
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
}
