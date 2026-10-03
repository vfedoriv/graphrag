package io.github.vfedoriv.graphrag.ai.profiles.api;

import io.github.vfedoriv.graphrag.ai.profiles.api.model.AiProfileResponse;
import io.github.vfedoriv.graphrag.ai.profiles.api.model.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.ai.profiles.api.model.UpdateAiProfileRequest;
import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-profiles")
public class AiProfileController {

    private final AiProfileService aiProfileService;

    public AiProfileController(AiProfileService aiProfileService) {
        this.aiProfileService = aiProfileService;
    }

    @PostMapping
    public AiProfileResponse create(@Valid @RequestBody CreateAiProfileRequest request) {
        return aiProfileService.create(request);
    }

    @GetMapping
    public List<AiProfileResponse> list() {
        return aiProfileService.list();
    }

    @GetMapping("/{profileId}")
    public AiProfileResponse get(@PathVariable String profileId) {
        return aiProfileService.get(profileId);
    }

    @PutMapping("/{profileId}")
    public AiProfileResponse update(
        @PathVariable String profileId,
        @Valid @RequestBody UpdateAiProfileRequest request
    ) {
        return aiProfileService.update(profileId, request);
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(@PathVariable String profileId) {
        aiProfileService.delete(profileId);
        return ResponseEntity.noContent().build();
    }
}
