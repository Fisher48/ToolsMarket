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

    /**
     * Имя кэша дерева категорий. В CaffeineCacheManager перечислен список имён
     * статически: обращение к не зарегистрированному имени даёт
     * IllegalArgumentException во время выполнения, поэтому новое имя обязано
     * быть здесь.
     */
    public static final String CATEGORY_TREE = "categoryTree";

    @Bean
    public CacheManager cacheManager() {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "loginAttempts", "sitemap", CATEGORY_TREE);

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

        // Дерево категорий: маленький и медленно меняющийся список, который
        // иначе читался из БД на каждой странице каталога
        cacheManager.registerCustomCache(CATEGORY_TREE,
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );

        return cacheManager;
    }
}
