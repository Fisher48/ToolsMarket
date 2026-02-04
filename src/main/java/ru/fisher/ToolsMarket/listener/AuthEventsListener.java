package ru.fisher.ToolsMarket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import ru.fisher.ToolsMarket.util.LoginAttemptService;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthEventsListener {

    private final LoginAttemptService attempts;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent e) {
        log.info("=== SPRING SECURITY AUTH SUCCESS EVENT ===");
        WebAuthenticationDetails details =
                (WebAuthenticationDetails) e.getAuthentication().getDetails();
        log.debug("Authentication success: {}", details.getRemoteAddress());
        attempts.loginSucceeded(details.getRemoteAddress());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent e) {
        log.error("=== SPRING SECURITY AUTH FAILURE EVENT ===");
        WebAuthenticationDetails details =
                (WebAuthenticationDetails) e.getAuthentication().getDetails();
        log.debug("Authentication failure: {}", details.getRemoteAddress());
        attempts.loginFailed(details.getRemoteAddress());
    }
}
