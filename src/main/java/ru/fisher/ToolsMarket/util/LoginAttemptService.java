package ru.fisher.ToolsMarket.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Service
@Slf4j
public class LoginAttemptService {

    private final Cache<String, AtomicInteger> attemptsCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .maximumSize(10000)
                    .build();

    public void loginFailed(String ip) {
        AtomicInteger attempts = attemptsCache.get(ip, k -> new AtomicInteger(0));
        assert attempts != null;
        int newCount = attempts.incrementAndGet();
        log.debug("Login failed for IP: {}, attempts: {}", ip, newCount);
    }

    public void loginSucceeded(String ip) {
        attemptsCache.invalidate(ip);
        log.debug("Login succeeded for IP: {}, attempts reset", ip);
    }

    public boolean isCaptchaRequired(String ip, int maxWithoutCaptcha) {
        AtomicInteger attempts = attemptsCache.getIfPresent(ip);
        if (attempts == null) {
            return false;
        }
        boolean required = attempts.get() >= maxWithoutCaptcha;
        log.debug("IP: {}, attempts: {}, captcha required: {}",
                ip, attempts.get(), required);
        return required;
    }

    public int getAttemptsCount(String ip) {
        AtomicInteger attempts = attemptsCache.getIfPresent(ip);
        return attempts != null ? attempts.get() : 0;
    }

    public void clearAll() {
        attemptsCache.invalidateAll();
        log.info("All login attempts cleared");
    }
}
