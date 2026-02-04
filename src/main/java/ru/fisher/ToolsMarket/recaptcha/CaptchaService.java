package ru.fisher.ToolsMarket.recaptcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.exceptions.CaptchaException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaService {

    private final RecaptchaConfig config;
    private final RecaptchaV2Provider v2Provider;
    private final RecaptchaV3Provider v3Provider;

    public void verify(String token, String ip, String action) throws CaptchaException {
        getProvider().verify(token, ip, action);
    }

    public String getSiteKey() {
        return getProvider().getSiteKey();
    }

    public String getVersion() {
        return getProvider().getVersion();
    }

    public boolean isEnabled() {
        return getProvider().isEnabled();
    }

    private CaptchaProvider getProvider() {
        return switch (config.getVersion()) {
            case V2 -> v2Provider;
            case V3 -> v3Provider;
            default -> v3Provider;
        };
    }

    // Утилитные методы
    public boolean isV2() {
        return config.getVersion() == RecaptchaConfig.Version.V2;
    }

    public boolean isV3() {
        return config.getVersion() == RecaptchaConfig.Version.V3;
    }

    public String getScriptUrl() {
        if (isV2()) {
            return "https://www.google.com/recaptcha/api.js";
        } else {
            return "https://www.google.com/recaptcha/api.js?render=" + getSiteKey();
        }
    }
}
