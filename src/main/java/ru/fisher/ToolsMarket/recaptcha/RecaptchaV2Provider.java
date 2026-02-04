package ru.fisher.ToolsMarket.recaptcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import ru.fisher.ToolsMarket.exceptions.CaptchaException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecaptchaV2Provider implements CaptchaProvider {

    private final RecaptchaConfig config;
    private final RestTemplate restTemplate;

    @Override
    public void verify(String token, String ip, String action) throws CaptchaException {
        if (!config.isEnabled()) {
            log.debug("CAPTCHA disabled, skipping verification");
            return;
        }

        log.debug("Verifying reCAPTCHA v2 for IP: {}", ip);

        if (!StringUtils.hasText(token)) {
            throw new CaptchaException("CAPTCHA token is empty");
        }

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", config.getSecretKey());
            params.add("response", token);
            params.add("remoteip", ip);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    config.getVerifyUrl(),
                    params,
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                log.error("reCAPTCHA v2 verification failed: {}", body);
                throw new CaptchaException("CAPTCHA verification failed");
            }

            log.debug("reCAPTCHA v2 verification successful");

        } catch (Exception e) {
            log.error("Error verifying reCAPTCHA v2", e);
            throw new CaptchaException("CAPTCHA verification error: " + e.getMessage());
        }
    }

    @Override
    public String getSiteKey() {
        return config.getSiteKey();
    }

    @Override
    public String getVersion() {
        return "v2";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
