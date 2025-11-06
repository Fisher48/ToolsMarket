package ru.fisher.ToolsMarket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.path:./uploads/images}")
    private String uploadPath;

//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/images/**")
//                .addResourceLocations("file:" + uploadPath + "/")
//                .setCachePeriod(3600)
//                .resourceChain(true);
//
//        log.info("Configured static resource handler for images: file:{}/", uploadPath);
//    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(
                        "file:" + uploadPath + "/",           // Основные изображения
                        "file:" + uploadPath + "/categories/", // Изображения категорий
                        "file:" + uploadPath + "/products/"    // Изображения продуктов
                )
                .setCachePeriod(3600)
                .resourceChain(true);

        log.info("Configured static resource handlers for images");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Редирект /admin -> /admin/categories
        registry.addRedirectViewController("/admin", "/admin/categories");
    }
}
