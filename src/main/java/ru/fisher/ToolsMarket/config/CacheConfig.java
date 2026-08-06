package ru.fisher.ToolsMarket.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "loginAttempts", "sitemap");

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());

        // Настройка для sitemap
        cacheManager.registerCustomCache("sitemap",
                Caffeine.newBuilder()
                        .maximumSize(5)                // максимум 5 версий sitemap
                        .expireAfterWrite(12, TimeUnit.HOURS)  // 12 часов
                        .recordStats()                 // для мониторинга
                        .build()
        );

        return cacheManager;
    }
}
