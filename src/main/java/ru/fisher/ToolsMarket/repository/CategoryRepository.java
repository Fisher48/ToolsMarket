package ru.fisher.ToolsMarket.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.config.CacheConfig;
import ru.fisher.ToolsMarket.models.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    /**
     * Сброс кэша дерева категорий сделан здесь, а не в CategoryService: категории
     * меняются ещё и импортёрами (YmlCategoryImporter, ExcelProductImportService),
     * которые пишут в репозиторий мимо сервиса. Любая запись в таблицу категорий
     * должна инвалидировать кэш меню каталога.
     */
    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_TREE, allEntries = true)
    Category save(Category category);

    @Override
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_TREE, allEntries = true)
    void deleteById(Long id);

    Optional<Category> findByTitle(String title);

    List<Category> findAllByOrderByNameAsc();

    @Query("SELECT c.title as title, c.createdAt as createdAt FROM Category c")
    List<Object[]> findAllForSitemap();

    @Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.parent
        LEFT JOIN FETCH c.children
        WHERE c.title = :title
    """)
    Optional<Category> findByTitleWithJoins(@Param("title") String title);

    @Query("""
        SELECT DISTINCT c FROM Category c
        LEFT JOIN FETCH c.children
        WHERE c.parent IS NULL
        ORDER BY c.sortOrder
    """)
    List<Category> findByParentIsNullOrderBySortOrderAsc();

    // Загрузка всех атрибутов и связанных категорий
    @Query("SELECT DISTINCT c FROM Category c " +
            "LEFT JOIN FETCH c.attributes " +
            "LEFT JOIN FETCH c.parent " +
            "LEFT JOIN FETCH c.children " +
            "ORDER BY c.sortOrder, c.name")
    List<Category> findAllWithAttributes();

    // Для поиска с сортировкой
    @Query("SELECT c FROM Category c " +
            "LEFT JOIN FETCH c.parent " +
            "ORDER BY c.sortOrder ASC, c.name ASC")
    List<Category> findAllWithParentOrdered();

    @EntityGraph(attributePaths = {"attributes", "parent", "children"})
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdWithRelations(@Param("id") Long id);

    List<Category> findByParent_IdOrderBySortOrderAsc(Long parentId);
    boolean existsByTitle(String title);
    List<Category> findByParentIsNull(Sort sort);
    List<Category> findByParentId(Long parentId);
}
