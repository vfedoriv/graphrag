package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.dto.ChunkingStateDtos.ChunkingStateResponse;
import io.github.vfedoriv.graphrag.service.ChunkingStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/chunking-state")
public class ChunkingStateController {
    private final ChunkingStateService service;

    public ChunkingStateController(ChunkingStateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Read effective chunking state", description = "Authoritative read model for effective chunking settings and revisions.")
    public ChunkingStateResponse get() {
        return service.get();
    }
}
