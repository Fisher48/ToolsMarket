package ru.fisher.ToolsMarket.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.fisher.ToolsMarket.dto.CategoryDto;
import ru.fisher.ToolsMarket.models.Category;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.LOOSE)
                .setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE);

        // Настройка для Category -> CategoryDto
        modelMapper.typeMap(Category.class, CategoryDto.class)
                .addMappings(mapper -> {
                    mapper.map(Category::getId, CategoryDto::setId);
                    mapper.map(Category::getTitle, CategoryDto::setTitle);
                    mapper.map(Category::getName, CategoryDto::setName);
                    mapper.map(Category::getDescription, CategoryDto::setDescription);
                    mapper.map(Category::getSortOrder, CategoryDto::setSortOrder);
                    mapper.map(Category::getImageUrl, CategoryDto::setImageUrl);
                    mapper.map(Category::getThumbnailUrl, CategoryDto::setThumbnailUrl);
                    mapper.map(Category::getCreatedAt, CategoryDto::setCreatedAt);

                    // Кастомный маппинг для родителя
                    mapper.using(ctx -> {
                        Category parent = ((Category) ctx.getSource()).getParent();
                        return parent != null ? parent.getId() : null;
                    }).map(Category::getParent, CategoryDto::setParentId);

                    mapper.using(ctx -> {
                        Category parent = ((Category) ctx.getSource()).getParent();
                        return parent != null ? parent.getName() : null;
                    }).map(Category::getParent, CategoryDto::setParentName);

                    // Пропускаем рекурсивные поля
                    // mapper.skip(CategoryDto::setParentName);
                    mapper.skip(CategoryDto::setChildren);
                });

        return modelMapper;
    }
}
