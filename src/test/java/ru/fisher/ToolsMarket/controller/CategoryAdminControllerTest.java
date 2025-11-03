package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.service.CategoryService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void index_ShouldReturnCategoriesList() throws Exception {
        // Given
        Category category = createTestCategory();
        when(categoryService.findAllEntities()).thenReturn(List.of(category));

        // When & Then
        mockMvc.perform(get("/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/index"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attribute("categories", List.of(category)));
    }

    @Test
    void show_WhenCategoryExists_ShouldReturnCategoryView() throws Exception {
        // Given
        Category category = createTestCategory();
        when(categoryService.findEntityById(1L)).thenReturn(Optional.of(category));

        // When & Then
        mockMvc.perform(get("/admin/categories/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/show"))
                .andExpect(model().attributeExists("category"))
                .andExpect(model().attribute("category", category));
    }

    @Test
    void show_WhenCategoryNotExists_ShouldReturnNotFound() throws Exception {
        // Given
        when(categoryService.findEntityById(1L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/admin/categories/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void newCategory_ShouldReturnNewCategoryForm() throws Exception {
        // Given
        when(categoryService.findAllEntities()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/categories/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/new"))
                .andExpect(model().attributeExists("category"))
                .andExpect(model().attributeExists("allCategories"));
    }

    @Test
    void create_ShouldSaveCategoryWithoutParentAndRedirect() throws Exception {
        // Given
        Category category = createTestCategory();
        when(categoryService.saveEntity(any(Category.class))).thenReturn(category);

        // When & Then
        mockMvc.perform(post("/admin/categories")
                        .param("name", "New Category")
                        .param("title", "new-category")
                        .param("description", "New description")
                        .param("sortOrder", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        verify(categoryService, times(1)).saveEntity(any(Category.class));
    }

    @Test
    void create_ShouldSaveCategoryWithParentAndRedirect() throws Exception {
        // Given
        Category parentCategory = createTestCategory();
        Category childCategory = createTestCategoryWithParent();

        when(categoryService.findEntityById(2L)).thenReturn(Optional.of(parentCategory));
        when(categoryService.saveEntity(any(Category.class))).thenReturn(childCategory);

        // When & Then
        mockMvc.perform(post("/admin/categories")
                        .param("name", "Child Category")
                        .param("title", "child-category")
                        .param("parent", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        verify(categoryService, times(1)).saveEntity(any(Category.class));
    }

    @Test
    void edit_WhenCategoryExists_ShouldReturnEditForm() throws Exception {
        // Given
        Category category = createTestCategory();
        when(categoryService.findEntityById(1L)).thenReturn(Optional.of(category));
        when(categoryService.findAllEntities()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/categories/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/edit"))
                .andExpect(model().attributeExists("category"))
                .andExpect(model().attributeExists("allCategories"));
    }

    @Test
    void update_ShouldUpdateCategoryAndRedirect() throws Exception {
        // Given
        Category existingCategory = createTestCategory();
        when(categoryService.findEntityById(1L)).thenReturn(Optional.of(existingCategory));
        when(categoryService.saveEntity(any(Category.class))).thenReturn(existingCategory);

        // When & Then
        mockMvc.perform(post("/admin/categories/1")
                        .param("name", "Updated Category")
                        .param("title", "updated-category")
                        .param("description", "Updated description")
                        .param("sortOrder", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        verify(categoryService, times(1)).saveEntity(any(Category.class));
    }

    @Test
    void delete_ShouldDeleteCategoryAndRedirect() throws Exception {
        // Given
        doNothing().when(categoryService).deleteEntity(1L);

        // When & Then
        mockMvc.perform(post("/admin/categories/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        verify(categoryService, times(1)).deleteEntity(1L);
    }

    @Test
    void create_WithInvalidData_ShouldReturnFormWithErrors() throws Exception {
        // Given
        when(categoryService.findAllEntities()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(post("/admin/categories")
                        .param("name", "") // пустое имя - ошибка валидации
                        .param("title", "")) // пустой title - ошибка валидации
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/new"))
                .andExpect(model().attributeHasErrors("category"))
                .andExpect(model().attributeExists("allCategories"));
    }

    private Category createTestCategory() {
        return Category.builder()
                .id(1L)
                .name("Test Category")
                .title("test-category")
                .description("Test description")
                .children(new HashSet<>())
                .sortOrder(0)
                .createdAt(Instant.now())
                .build();
    }

    private Category createTestCategoryWithParent() {
        Category parent = Category.builder()
                .id(2L)
                .name("Parent Category")
                .title("parent-category")
                .build();

        return Category.builder()
                .id(1L)
                .name("Child Category")
                .title("child-category")
                .parent(parent)
                .build();
    }

}