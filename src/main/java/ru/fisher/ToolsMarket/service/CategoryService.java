package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.config.CacheConfig;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryAdminDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryPageData;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategorySpecification;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductCardDto;
import ru.fisher.ToolsMarket.mapper.CategoryMapperService;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.repository.CategoryJdbcRepository;
import ru.fisher.ToolsMarket.repository.CategoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryJdbcRepository categoryJdbcRepository;
    private final CategoryMapperService categoryMapperService;

    @Transactional(readOnly = true)
    public List<Category> findAllEntities() {
        return categoryRepository.findAllWithAttributes(); // метод с JOIN FETCH
    }

    public Optional<Category> findEntityById(Long id) {
        return categoryRepository.findByIdWithRelations(id);
    }

    @Transactional
    public Category saveEntity(Category category) {
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteEntity(Long id) {
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Category> findByIds(List<Long> ids) {
        return categoryRepository.findAllById(ids);
    }

    /**
     * Корневые категории для меню каталога. Метод зовётся из @ModelAttribute
     * на каждом запросе каталога, поэтому список кэшируется; сброс cache делает
     * CategoryRepository при любой записи категории (см. @CacheEvict там).
     */
    @Cacheable(cacheNames = CacheConfig.CATEGORY_TREE, key = "'root'")
    public List<CategoryDto> getRootCategories() {
        return categoryRepository.findByParentIsNullOrderBySortOrderAsc().stream()
                .map(categoryMapperService::toDto) // Используем простой DTO
                .toList();
    }

    // Метод для получения только родительских категорий для главной страницы
    @Cacheable(cacheNames = CacheConfig.CATEGORY_TREE, key = "'home'")
    public List<CategoryDto> getParentCategoriesForHome() {
        long start = System.nanoTime();

        try {
            List<Category> categories = categoryRepository.findByParentIsNullOrderBySortOrderAsc();
            return categoryMapperService.toDtoList(categories);

        } finally {
            long duration = System.nanoTime() - start;
            log.debug("Загрузка категорий заняла: {} мс", duration / 1_000_000);
        }
    }

    public Optional<CategoryDto> findByTitle(String title) {
        return categoryRepository.findByTitleWithJoins(title)
                .map(categoryMapperService::toDto);
    }

    public Optional<CategoryDto> findById(Long id) {
        return categoryRepository.findById(id)
                .map(categoryMapperService::toDto);
    }

    public List<CategoryDto> findAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapperService::toDto)
                .toList();
    }

    public List<Category> findAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public Page<CategoryAdminDto> search(
            String name,
            String title,
            Long parentId,
            Pageable pageable) {

        Specification<Category> spec = (root, query, cb) ->
                cb.conjunction();

        if (name != null && !name.isBlank()) {
            spec = spec.and(CategorySpecification.nameLike(name));
        }

        if (title != null && !title.isBlank()) {
            spec = spec.and(CategorySpecification.titleLike(title));
        }

        if (parentId != null) {
            spec = spec.and(CategorySpecification.hasParent(parentId));
        }

        Page<Category> categoryPage = categoryRepository.findAll(spec, pageable);

        return categoryPage.map(category -> {
            CategoryDto dto = categoryMapperService.toDto(category);
            return categoryMapperService.convertToAdminDto(category, dto);
        });
    }

    // Метод для получения всех категорий с сортировкой
    public List<Category> findAllEntitiesSorted() {
        return categoryRepository.findAllWithParentOrdered();
    }

    /**
     * Получение всех данных для страницы категории
     */
    @Transactional(readOnly = true)
    public CategoryPageData getCategoryPage(String title, Long userId, String sort, int page, int size) {
        // 1. Категория
        CategoryDto category = findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 2. Товары через JDBC (вместе со скидкой и признаком «в корзине»)
        Page<ProductCardDto> products = categoryJdbcRepository.findProductsByCategory(
                category.getId(), userId, sort, page, size
        );

        // 3. Количество товаров в корзине берём из тех же строк товаров: in_cart и
        // cart_quantity уже посчитаны в SQL страницы категории. Отдельный запрос за
        // корзиной не нужен — он ещё и создавал пустую корзину на обычном GET
        Map<Long, Integer> cartProductQuantities = products.getContent().stream()
                .filter(card -> card.isInCart() && card.getCartQuantity() > 0)
                .collect(Collectors.toMap(
                        ProductCardDto::getId,
                        ProductCardDto::getCartQuantity,
                        (existing, replacement) -> existing
                ));

        // 4. Общее количество товаров уже посчитано внутри findProductsByCategory
        return CategoryPageData.builder()
                .category(category)
                .products(products)
                .cartProductQuantities(cartProductQuantities)
                .totalElements(products.getTotalElements())
                .build();
    }

    public List<Object[]> findAllForSitemap() {
        return categoryRepository.findAllForSitemap();
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
