package com.proximity.adapter.in.web;

import com.proximity.application.port.in.CategoryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryUseCase categoryUseCase;

    public CategoryController(CategoryUseCase categoryUseCase) {
        this.categoryUseCase = categoryUseCase;
    }

    @GetMapping
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(categoryUseCase.getCategories());
    }
}
