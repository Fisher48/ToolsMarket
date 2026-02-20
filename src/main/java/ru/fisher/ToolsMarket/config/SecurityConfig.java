package ru.fisher.ToolsMarket.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                        ).hasRole("ADMIN")

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
                        .defaultSuccessUrl("/", true)
                        .failureHandler(failureHandler)
                        //.failureUrl("/auth/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
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
                .userDetailsService(userDetailsService);

        return http.build();

    }

    @Bean
    public PasswordEncoder getPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
