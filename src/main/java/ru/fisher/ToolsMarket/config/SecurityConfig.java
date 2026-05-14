package ru.fisher.ToolsMarket.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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
    @Order(1)
    public SecurityFilterChain staticResourcesFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/css/**", "/js/**", "/images/**", "/webjars/**",
                        "/logo.png", "/favicon.ico", "/favicon-*.png",
                        "/apple-touch-icon.png", "/site.webmanifest"
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
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
                        .clearAuthentication(true)
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession()
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/auth/login?expired")
                        .sessionRegistry(sessionRegistry())
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
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

        @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
