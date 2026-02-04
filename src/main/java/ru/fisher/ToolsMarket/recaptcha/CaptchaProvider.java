package ru.fisher.ToolsMarket.recaptcha;

import ru.fisher.ToolsMarket.exceptions.CaptchaException;

public interface CaptchaProvider {
    void verify(String token, String ip, String action) throws CaptchaException;
    String getSiteKey();
    String getVersion();
    boolean isEnabled();
}
