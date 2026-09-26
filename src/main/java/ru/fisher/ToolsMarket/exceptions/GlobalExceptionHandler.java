package ru.fisher.ToolsMarket.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private enum ResponseKind {
        JSON, HTML, NO_BODY
    }

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

    @ExceptionHandler(DuplicateSkuException.class)
    public String handleDuplicateSku(DuplicateSkuException e,
                                     RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/admin/products/new";
    }

    // Обработка ошибки при разрыве соединения
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    @ResponseStatus(HttpStatus.OK)
    public void handleClientAbort(Exception e) {
        log.debug("Client disconnected before response could be sent: {}", e.getMessage());
        // Ничего не делаем - клиент уже ушел
    }

    // Ловим обрыв соединения клиентом
//    @ExceptionHandler({ClientAbortException.class, IOException.class})
//    public void handleClientAbortException(Exception ex) {
//        if (ex.getMessage() != null &&
//                (ex.getMessage().contains("Broken pipe") || ex.getMessage().contains("Connection reset by peer"))) {
//            // Либо вообще ничего не делаем, либо пишем в дебаг, чтобы не засорять ERROR логи
//            log.debug("Client disconnected before response could be completely sent: {}", ex.getMessage());
//            return;
//        }
//        // Если это другая IOException, логируем как обычно
//        log.error("I/O error occurred", ex);
//    }

    @ExceptionHandler(MultipartException.class)
    public String handleMultipartException(MultipartException e,
                                           HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
        // Для отслеживания ботов
        log.warn("Multipart exception - IP: {}, URI: {}, User-Agent: {}",
                request.getRemoteAddr(),
                request.getRequestURI(),
                request.getHeader("User-Agent"));

        // Для AJAX запросов возвращаем JSON
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid multipart request");
        }

        // Для обычных запросов - редирект
        redirectAttributes.addFlashAttribute("errorMessage",
                "Ошибка загрузки файла. Возможно, файл слишком большой.");
        return "error/error";
    }

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

    // Боты долбят GET-only страницы (POST /, POST на статику) и на каждый запрос
    // попадали в handleAllExceptions с ERROR-логом без URL. Отвечаем тихим WARN
    // с адресом и реальным статусом 405 вместо 500.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                               HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        log.warn("405: метод {} на путь {} (UA={}, Referer={})",
                request.getMethod(), request.getRequestURI(), userAgent, request.getHeader("Referer"));

        ModelAndView mav = new ModelAndView("error/405");
        mav.setStatus(HttpStatus.METHOD_NOT_ALLOWED);
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleAllExceptions(Exception ex, Model model, HttpServletRequest request) {
        log.error("Необработанное исключение на {}: ", request.getRequestURI(), ex);

        // error/error.html ждёт errorCode/errorMessage, а не error/status
        model.addAttribute("errorCode", 500);
        model.addAttribute("errorMessage", ex.getMessage());

        ModelAndView mav = new ModelAndView("error/error");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("errorCode", 404);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/error";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = "Проверьте правильность заполнения полей";

        ResponseKind kind = responseKind(request);

        if (kind == ResponseKind.JSON) {
            Map<String, String> errors = new HashMap<>();
            ex.getBindingResult().getFieldErrors().forEach(error -> {
                errors.put(error.getField(), error.getDefaultMessage());
            });

            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("status", status.value());
            response.put("errors", errors);
            response.put("message", message);
            response.put("path", request.getRequestURI());

            return new ResponseEntity<>(response, status);
        }

        if (kind == ResponseKind.NO_BODY) {
            return ResponseEntity.status(status).build();
        }

        log.warn("Ошибка валидации на {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getBindingResult().getAllErrors());

        return errorView(status, message, request.getRequestURI());
    }

    /**
     * ResponseStatusException бросается из обычных страниц (несуществующая категория,
     * товар и т.п.), поэтому браузеру здесь нужен HTML, а не JSON: попытка отдать JSON
     * в ответ на запрос с Accept: text/html роняла сам обработчик с
     * HttpMediaTypeNotAcceptableException и превращала 404 в 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : defaultMessage(status);

        log.warn("{} {}: {}", status.value(), request.getRequestURI(), reason);

        ResponseKind kind = responseKind(request);

        if (kind == ResponseKind.JSON) {
            Map<String, Object> body = new HashMap<>();
            body.put("timestamp", LocalDateTime.now());
            body.put("status", status.value());
            body.put("error", reason);
            body.put("path", request.getRequestURI());

            return new ResponseEntity<>(body, status);
        }

        if (kind == ResponseKind.NO_BODY) {
            return ResponseEntity.status(status).build();
        }

        return errorView(status, reason, request.getRequestURI());
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public Object handleMissingCookie(MissingRequestCookieException ex,
                                      HttpServletRequest request) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = "Требуется cookie '%s'".formatted(ex.getCookieName());

        ResponseKind kind = responseKind(request);

        if (kind == ResponseKind.JSON) {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now());
            response.put("status", status.value());
            response.put("error", "Bad Request");
            response.put("message", message);
            response.put("path", request.getRequestURI());

            return new ResponseEntity<>(response, status);
        }

        if (kind == ResponseKind.NO_BODY) {
            return ResponseEntity.status(status).build();
        }

        log.warn("Отсутствует cookie '{}' на {} {}", ex.getCookieName(), request.getMethod(),
                request.getRequestURI());

        return errorView(status, message, request.getRequestURI());
    }

    /**
     * Запрос, для которого невозможно подобрать представление (например,
     * Accept: application/xml). Без отдельного обработчика он уходил в общий
     * Exception и отдавался как 500 со стектрейсом.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {

        log.warn("406 на {} {}: неприемлемый Accept: {}",
                request.getMethod(), request.getRequestURI(), request.getHeader("Accept"));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_ACCEPTABLE.value());
        body.put("error", "Неприемлемый тип ответа");
        body.put("path", request.getRequestURI());

        return new ResponseEntity<>(body, HttpStatus.NOT_ACCEPTABLE);
    }

    /**
     * JSON отдаём API-клиентам и XHR, HTML — браузеру.
     *
     * Порядок проверок важен: браузер шлёт Accept вида "text/html,application/xml;q=0.9,
     * wildcard;q=0.8", поэтому явный text/html проверяется раньше catch-all на
     * wildcard. Пустой Accept и "только wildcard" (так ходят все fetch() проекта)
     * считаем JSON — иначе бейдж корзины и другие ajax-запросы получили бы HTML
     * вместо разобранного ответа. Если клиент не принимает ни HTML, ни JSON
     * (Accept: application/xml) — не отдаём тело вовсе: рендер страницы всё равно
     * упал бы с HttpMediaTypeNotAcceptableException.
     */
    private ResponseKind responseKind(HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseKind.JSON;
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return ResponseKind.JSON;
        }

        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return ResponseKind.JSON;
        }
        if (accept.contains("text/html")) {
            return ResponseKind.HTML;
        }
        if (accept.contains("application/json")) {
            return ResponseKind.JSON;
        }
        if (accept.contains("*/*")) {
            return ResponseKind.JSON;
        }
        return ResponseKind.NO_BODY;
    }

    /**
     * Страница ошибки по коду статуса — тот же набор, что и в CustomErrorController,
     * чтобы пользователь видел одинаковую страницу при прямом попадании в обработчик
     * и при переходе через /error.
     */
    private ModelAndView errorView(HttpStatus status, String message, String path) {
        String view = switch (status.value()) {
            case 403 -> "error/403";
            case 404 -> "error/404";
            case 500 -> "error/500";
            default -> "error/error";
        };

        ModelAndView mav = new ModelAndView(view);
        mav.setStatus(status);
        mav.addObject("errorCode", status.value());
        mav.addObject("errorMessage", message);
        mav.addObject("path", path);
        return mav;
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "Неверный запрос";
            case 401 -> "Требуется авторизация";
            case 403 -> "Доступ запрещен";
            case 404 -> "Страница не найдена";
            case 405 -> "Метод не поддерживается";
            case 500 -> "Внутренняя ошибка сервера";
            default -> "Ошибка " + status.value();
        };
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ModelAndView handleAccessDenied(AuthorizationDeniedException ex,
                                           HttpServletRequest request) {
        log.warn("Access denied for user trying to access: {}", request.getRequestURI());

        ModelAndView mav = new ModelAndView("error/403");
        mav.addObject("error", "Доступ запрещен");
        mav.addObject("message", "У вас нет прав для просмотра этой страницы");
        mav.addObject("path", request.getRequestURI());

        return mav;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(Model model, HttpServletRequest request) {
        log.warn("404 error for path: {}", request.getRequestURI());

        model.addAttribute("timestamp", LocalDateTime.now());

        return "error/404";
    }
}
