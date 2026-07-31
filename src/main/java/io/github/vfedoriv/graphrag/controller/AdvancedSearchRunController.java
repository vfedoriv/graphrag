package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRunStatus;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.CreateRequest;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.ResultResponse;
import io.github.vfedoriv.graphrag.dto.AdvancedSearchRunDtos.RunResponse;
import io.github.vfedoriv.graphrag.dto.PageResponse;
import io.github.vfedoriv.graphrag.service.AdvancedSearchRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs")
public class AdvancedSearchRunController {
    private final AdvancedSearchRunService service;
    public AdvancedSearchRunController(AdvancedSearchRunService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunResponse create(@PathVariable String knowledgeBaseId, @Valid @RequestBody CreateRequest request) {
        return service.create(knowledgeBaseId, request);
    }

    @GetMapping
    public PageResponse<RunResponse> list(
        @PathVariable String knowledgeBaseId,
        @RequestParam(required = false) AdvancedSearchRunStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { return service.list(knowledgeBaseId, status, page, size); }

    @GetMapping("/{runId}")
    public RunResponse status(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.get(knowledgeBaseId, runId);
    }

    @GetMapping("/{runId}/result")
    public ResultResponse result(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.result(knowledgeBaseId, runId);
    }

    @PostMapping("/{runId}/cancel")
    public RunResponse cancel(@PathVariable String knowledgeBaseId, @PathVariable String runId) {
        return service.cancel(knowledgeBaseId, runId);
    }
}
