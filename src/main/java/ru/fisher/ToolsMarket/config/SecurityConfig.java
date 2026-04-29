package ru.fisher.ToolsMarket.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.fisher.ToolsMarket.recaptcha.RecaptchaValidationFilter;
import ru.fisher.ToolsMarket.service.UserDetailServiceImpl;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final UserDetailServiceImpl userDetailsService;
    private final RecaptchaValidationFilter recaptchaValidationFilter;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final CustomAuthenticationSuccessHandler successHandler;

    @Value("${app.security.remember-me.key}")
    private String uniqueSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(
                        recaptchaValidationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .csrf(csrf ->
                        csrf.ignoringRequestMatchers("/actuator/**"))
                .authorizeHttpRequests(auth -> auth

                        // Actuator
                        .requestMatchers(
                                "/actuator/prometheus",
                                "/actuator/health"
                        ).permitAll()

                        // Публичные
                        .requestMatchers(
                                "/", "/index", "/error",
                                "/css/**", "/js/**", "/images/**", "/webjars/**",
                                "/logo.png",                                  // логотип
                                "/favicon.ico", "/favicon-*.png",             // favicon
                                "/apple-touch-icon.png", "/site.webmanifest", // apple и manifest
                                "/search/**", "/catalog/**", "/product/**",
                                "/category/**", "/api/public/**"
                        ).permitAll()

                        // Точки входа для авторизации
                        .requestMatchers(
                                "/auth/**", "/register",
                                "/login", "/logout").permitAll()

                        // Точки входа для админа
                        .requestMatchers(
                                "/admin/**", "/api/admin/**", "/grafana/**"
                        ).hasAnyRole("ADMIN","MANAGER")

                        // Точки входа для пользователя
                        .requestMatchers( "/api/**",
                                "/order/**", "/cart/**",
                                "/profile/**", "/api/user/**"
                        ).authenticated()

                        // Все остальное требует аутентификации
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request,
                                               response,
                                               authentication) -> {
                            String referer = request.getHeader("Referer");
                            if (referer != null && !referer.contains("/login") && !referer.contains("/logout")) {
                                response.sendRedirect(referer);
                            } else {
                                response.sendRedirect("/");
                            }
                        })
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                        .maximumSessions(1)
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/error/403")
                )
                .rememberMe(remember -> remember
                        .key(uniqueSecret)
                        .tokenValiditySeconds(120 * 24 * 60 * 60)  // 120 дней для "Запомнить меня"
                        .rememberMeParameter("remember-me")
                        .userDetailsService(userDetailsService)
                )
                .userDetailsService(userDetailsService);

        return http.build();

    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
