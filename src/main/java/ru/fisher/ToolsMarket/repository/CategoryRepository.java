package ru.fisher.ToolsMarket.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByTitle(String title);

    List<Category> findByParentIsNullOrderBySortOrderAsc();

    List<Category> findByParent_IdOrderBySortOrderAsc(Long parentId);
    boolean existsByTitle(String title);
    List<Category> findByParentIsNull(Sort sort);
    List<Category> findByParentId(Long parentId);
}
