package com.proximity.adapter.in.web;

import com.proximity.application.dto.SearchRequest;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.port.in.SearchUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchUseCase searchUseCase;

    public SearchController(SearchUseCase searchUseCase) {
        this.searchUseCase = searchUseCase;
    }

    @GetMapping
    public ResponseEntity<SearchResponse> search(@Valid SearchRequest request) {
        SearchResponse response = searchUseCase.search(request);
        return ResponseEntity.ok(response);
    }
}
