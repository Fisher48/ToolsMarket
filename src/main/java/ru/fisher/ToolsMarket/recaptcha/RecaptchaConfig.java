package ru.fisher.ToolsMarket.recaptcha;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.recaptcha")
@Data
public class RecaptchaConfig {

    public enum Version { V2, V3 }

    private Version version = Version.V3;
    private String siteKey;
    private String secretKey;
    private String verifyUrl = "https://www.google.com/recaptcha/api/siteverify";
    private float minScore = 0.5f;
    private boolean enabled = true;

    // Новые настройки
    private boolean alwaysOnRegister = true;  // Всегда требовать на регистрации
    private boolean alwaysOnLogin = false;    // Только при неудачных попытках
    private String registerAction = "register";
    private String loginAction = "login";
}
