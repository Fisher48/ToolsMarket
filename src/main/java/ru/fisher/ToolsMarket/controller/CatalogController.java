package ru.fisher.ToolsMarket.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.util.List;


@Controller
@RequestMapping()
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public CatalogController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        // Добавляем корневые категории во все модели для меню навигации
        List<Category> rootCategories = categoryService.getRootCategories();
        model.addAttribute("categories", rootCategories);
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Page<Product> searchResults;

        if (q == null || q.trim().isEmpty()) {
            searchResults = Page.empty();
        } else {
            searchResults = productService.search(q.trim(), PageRequest.of(page, 12));
        }

        model.addAttribute("query", q);
        model.addAttribute("results", searchResults);
        model.addAttribute("resultsCount", searchResults.getTotalElements());

        return "catalog/search";
    }


    @GetMapping("/category/{title}")
    public String category(@PathVariable String title,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Category cat = categoryService.findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Page<Product> products = productService.findByCategory(cat.getId(), PageRequest.of(page, 12));

        model.addAttribute("category", cat);
        model.addAttribute("products", products);
        return "catalog/category";
    }


    @GetMapping("/product/{title}")
    public String product(@PathVariable String title, Model model) {
        Product product = productService.findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        model.addAttribute("product", product);
        return "catalog/product";
    }
}
