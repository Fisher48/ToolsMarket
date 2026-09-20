package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Attribute;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    List<Attribute> findByCategoryIdOrderBySortOrder(Long categoryId);

    // findFirst...OrderByIdAsc: детерминированно возвращаем самый старый атрибут,
    // если в БД завалялись дубли (category_id, name) — find... без лимита упал бы
    // с IncorrectResultSizeDataAccessException.
    Optional<Attribute> findFirstByCategoryIdAndNameOrderByIdAsc(Long categoryId, String name);

    @Query("""
       SELECT COALESCE(MAX(a.sortOrder), 0)
       FROM Attribute a
       WHERE a.category.id = :categoryId
       """)
    int findMaxSortOrderByCategoryId(@Param("categoryId") Long categoryId);

    List<Attribute> findByCategoryIdAndFilterableTrueOrderBySortOrder(Long categoryId);

    List<Attribute> findByCategoryIdAndRequiredTrue(Long categoryId);

    @Query("SELECT a FROM Attribute a WHERE a.category.id IN :categoryIds")
    List<Attribute> findByCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    boolean existsByNameAndCategoryId(String name, Long categoryId);

    @Modifying
    @Query("DELETE FROM Attribute a WHERE a.category.id = :categoryId")
    void deleteByCategoryId(@Param("categoryId") Long categoryId);
}
