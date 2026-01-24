package ru.fisher.ToolsMarket.config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.fisher.ToolsMarket.service.UserDetailServiceImpl;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf ->
                        csrf.ignoringRequestMatchers("/actuator/**"))
                .authorizeHttpRequests(auth -> auth

                        // Actuator
                        .requestMatchers(
                                "/actuator/prometheus",
                                "/actuator/health"
                        ).permitAll()

                        // Публичные
                        .requestMatchers( "/",
                                "/css/**", "/js/**", "/images/**",
                                "/webjars/**",
                                "/error", "/catalog/**", "/product/**",
                                "/category/**", "/api/public/**").permitAll()

                        // Точки входа для авторизации
                        .requestMatchers(
                                "/auth/**", "/register",
                                "/login", "/logout").permitAll()

                        // Точки входа для админа
                        .requestMatchers(
                                "/admin/**", "api/admin/**", "/grafana/**"
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
                        .failureUrl("/auth/login?error=true")
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
