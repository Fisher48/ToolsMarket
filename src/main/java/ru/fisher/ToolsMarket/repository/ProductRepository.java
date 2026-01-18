package ru.fisher.ToolsMarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByTitle(String title);

    @Query("SELECT p FROM Product p " +
            "JOIN p.categories c " +
            "WHERE c.id = :categoryId AND p.active = true")
    Page<Product> findActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "WHERE p.active = true " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY " +
            "CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:q, '%')) THEN 1 " +
            "     WHEN LOWER(p.sku) = LOWER(:q) THEN 2 " +
            "     ELSE 3 END, p.name")
    Page<Product> searchActive(@Param("q") String q, Pageable pageable);

    boolean existsByTitle(String title);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.categories WHERE p.id = :id")
    Optional<Product> findByIdWithImagesAndCategories(@Param("id") Long id);

    boolean existsByCategoriesContaining(Category category);

    Page<Product> findByActiveTrue(Pageable pageable);

    // Метод с явной загрузкой всех связей для редактирования
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.categories c " +
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH c.attributes a " +
            "LEFT JOIN FETCH p.attributeValues av " +
            "LEFT JOIN FETCH av.attribute " +
            "WHERE p.id = :id")
    Optional<Product> findWithDetailsById(@Param("id") Long id);

    // Метод для страницы характеристик
    @EntityGraph(attributePaths = {"attributeValues", "attributeValues.attribute"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findWithAttributesById(@Param("id") Long id);

    // ИСПРАВЛЕННЫЙ МЕТОД - загружаем только одну коллекцию
    @EntityGraph(attributePaths = {"categories", "attributeValues", "attributeValues.attribute"})
    @Query("SELECT p FROM Product p WHERE p.title = :title")
    Optional<Product> findByTitleWithAttributes(@Param("title") String title);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.categories ORDER BY p.createdAt DESC")
    List<Product> findAllWithCategories();

    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.categories c " +
            "LEFT JOIN FETCH c.parent " + // если нужны родители категорий
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH p.attributeValues av " +
            "LEFT JOIN FETCH av.attribute a " +
            "LEFT JOIN FETCH a.category " + // категория атрибута
            "WHERE p.id = :id")
    Optional<Product> findByIdWithAllRelations(@Param("id") Long id);


//    @Query("SELECT p FROM Product p " +
//            "WHERE p.active = true " +
//            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
//            "OR p.description LIKE CONCAT('%', :q, '%'))")
//    Page<Product> searchActive(@Param("q") String q, Pageable pageable);
}
