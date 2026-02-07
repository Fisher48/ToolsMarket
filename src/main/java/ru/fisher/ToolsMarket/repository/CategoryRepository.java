package ru.fisher.ToolsMarket.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByTitle(String title);

    List<Category> findByParentIsNullOrderBySortOrderAsc();

    // Загрузка всех атрибутов и связанных категорий
    @Query("SELECT DISTINCT c FROM Category c " +
            "LEFT JOIN FETCH c.attributes " +
            "LEFT JOIN FETCH c.parent " +
            "LEFT JOIN FETCH c.children " +
            "ORDER BY c.sortOrder, c.name")
    List<Category> findAllWithAttributes();

    @EntityGraph(attributePaths = {"attributes", "parent", "children"})
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdWithRelations(@Param("id") Long id);

    List<Category> findByParent_IdOrderBySortOrderAsc(Long parentId);
    boolean existsByTitle(String title);
    List<Category> findByParentIsNull(Sort sort);
    List<Category> findByParentId(Long parentId);
}
