package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class ErrorController {

    @GetMapping("/403")
    public String accessDenied() {
        return "error/403";
    }

    @GetMapping("/404")
    public String notFound() {
        return "error/404";
    }

    @GetMapping("/500")
    public String serverError() {
        return "error/500";
    }

    // Глобальный обработчик ошибок
    @GetMapping
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        Integer statusCode = null;
        if (status != null) {
            statusCode = Integer.valueOf(status.toString());
        }

        model.addAttribute("errorCode", statusCode != null ? statusCode.toString() : "Ошибка");
        model.addAttribute("errorMessage", getErrorMessage(statusCode));
        model.addAttribute("errorDetails", message);

        if (exception != null && exception instanceof Exception) {
            model.addAttribute("errorTrace", ((Exception) exception).getMessage());
        }

        // Возвращаем соответствующую страницу
        if (statusCode != null) {
            return switch (statusCode) {
                case 403 -> "error/403";
                case 404 -> "error/404";
                case 500 -> "error/500";
                default -> "error/error";
            };
        }

        return "error/error";
    }

    private String getErrorMessage(Integer statusCode) {
        if (statusCode == null) {
            return "Произошла непредвиденная ошибка";
        }

        return switch (statusCode) {
            case 400 -> "Неверный запрос";
            case 401 -> "Требуется авторизация";
            case 403 -> "Доступ запрещен";
            case 404 -> "Страница не найдена";
            case 405 -> "Метод не поддерживается";
            case 408 -> "Время запроса истекло";
            case 500 -> "Внутренняя ошибка сервера";
            case 503 -> "Сервис временно недоступен";
            default -> "Ошибка " + statusCode;
        };
    }
}
