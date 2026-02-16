package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.dto.AttributeOrderDto;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.AttributeType;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.service.AttributeService;
import ru.fisher.ToolsMarket.service.CategoryService;

import java.util.List;

@Controller
@RequestMapping("/admin/categories/{categoryId}/attributes")
@RequiredArgsConstructor
public class AttributeAdminController {
    private final CategoryService categoryService;
    private final AttributeService attributeService;

    @GetMapping
    public String listAttributes(@PathVariable Long categoryId, Model model) {
        Category category = categoryService.findEntityById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<Attribute> attributes = attributeService.getAttributesByCategory(categoryId);

        model.addAttribute("category", category);
        model.addAttribute("attributes", attributes);
        return "admin/attributes/index";
    }

    @GetMapping("/new")
    public String newAttributeForm(@PathVariable Long categoryId, Model model) {
        Category category = categoryService.findEntityById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("category", category);
        model.addAttribute("attribute", new Attribute());
        model.addAttribute("attributeTypes", AttributeType.values());
        return "admin/attributes/new";
    }

    @PostMapping
    public String createAttribute(@PathVariable Long categoryId,
                                  @ModelAttribute Attribute attribute) {
        Category category = categoryService.findEntityById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        attribute.setCategory(category);
        attributeService.createAttribute(attribute);

        return "redirect:/admin/categories/" + categoryId + "/attributes";
    }

    @GetMapping("/{attributeId}/edit")
    public String editAttributeForm(@PathVariable Long categoryId,
                                    @PathVariable Long attributeId,
                                    Model model) {
        Attribute attribute = attributeService.findById(attributeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Category category = categoryService.findEntityById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("category", category);
        model.addAttribute("attribute", attribute);
        model.addAttribute("attributeTypes", AttributeType.values());
        return "admin/attributes/edit";
    }

    @PostMapping("/{attributeId}")
    public String updateAttribute(@PathVariable Long categoryId,
                                  @PathVariable Long attributeId,
                                  @ModelAttribute Attribute attribute) {
        Attribute existing = attributeService.findById(attributeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        existing.setName(attribute.getName());
        existing.setUnit(attribute.getUnit());
        existing.setType(attribute.getType());
        existing.setOptions(attribute.getOptions());
       // existing.setSortOrder(attribute.getSortOrder());
        existing.setRequired(attribute.isRequired());
        existing.setFilterable(attribute.isFilterable());

        attributeService.save(existing);
        return "redirect:/admin/categories/" + categoryId + "/attributes";
    }

    @PostMapping("/{attributeId}/delete")
    public String deleteAttribute(@PathVariable Long categoryId,
                                  @PathVariable Long attributeId) {
        attributeService.deleteAttribute(attributeId);
        return "redirect:/admin/categories/" + categoryId + "/attributes";
    }

    @PostMapping("/order")
    @ResponseBody
    public ResponseEntity<?> updateAttributesOrder(@PathVariable Long categoryId,
                                                   @RequestBody List<AttributeOrderDto> orderData) {
        try {
            attributeService.updateSortOrder(orderData);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
