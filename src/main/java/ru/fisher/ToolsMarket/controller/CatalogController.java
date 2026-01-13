package ru.fisher.ToolsMarket.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.List;


@Controller
@RequestMapping()
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UserService userService;

    public CatalogController(ProductService productService, CategoryService categoryService, UserService userService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.userService = userService;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        // Добавляем корневые категории
        List<CategoryDto> rootCategories = categoryService.getRootCategories();
        model.addAttribute("categories", rootCategories);

        // Добавляем информацию о пользователе и скидках
        if (userDetails != null) {
            userService.findByUsername(userDetails.getUsername())
                    .ifPresent(user -> {
                        model.addAttribute("currentUser", user);
                        model.addAttribute("userType", user.getUserType());
                        model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());
                    });
        }
    }

    @GetMapping("/product/{title}")
    public String product(@PathVariable String title,
                          @AuthenticationPrincipal UserDetails userDetails,
                          Model model) {

        // Получаем пользователя
        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        ProductDto product = productService.findByTitleWithDiscounts(title, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        model.addAttribute("product", product);
        return "catalog/product";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {

        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        Page<ProductListDto> searchResults;

        if (q == null || q.trim().isEmpty()) {
            searchResults = Page.empty();
        } else {
            searchResults = productService.searchWithDiscounts(q.trim(), user, PageRequest.of(page, 12));
        }

        model.addAttribute("query", q);
        model.addAttribute("results", searchResults);
        model.addAttribute("resultsCount", searchResults.getTotalElements());

        return "catalog/search";
    }


    @GetMapping("/category/{title}")
    public String category(@PathVariable String title,
                           @RequestParam(defaultValue = "0") int page,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {

        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        CategoryDto category = categoryService.findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Page<ProductListDto> products = productService.findByCategoryWithDiscounts(
                category.getId(), user, PageRequest.of(page, 12));

        model.addAttribute("category", category);
        model.addAttribute("products", products);
        return "catalog/category";
    }


//    @GetMapping("/product/{title}")
//    public String product(@PathVariable String title, Model model) {
//        ProductDto product = productService.findWithAttributesByTitle(title)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
//
//        model.addAttribute("product", product);
//        return "catalog/product";
//    }
}
