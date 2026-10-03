package io.github.vfedoriv.graphrag.controller;

import io.github.vfedoriv.graphrag.ai.profiles.api.AiProfileController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.ai.profiles.api.model.AiProfileResponse;
import io.github.vfedoriv.graphrag.ai.profiles.api.model.CreateAiProfileRequest;
import io.github.vfedoriv.graphrag.ai.profiles.api.model.UpdateAiProfileRequest;
import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AiProfileControllerTest {

    @Test
    void delegatesCrudToServiceAndReturnsMaskedResponses() {
        AiProfileService service = mock(AiProfileService.class);
        AiProfileController controller = new AiProfileController(service);
        AiProfileResponse response = response("profile-1");
        CreateAiProfileRequest create = new CreateAiProfileRequest(
            "profile-1",
            "Profile 1",
            "https://profiles.example/v1",
            "secret-key",
            "chat",
            "embed",
            768,
            null,
            null,
            false
        );
        UpdateAiProfileRequest update = new UpdateAiProfileRequest(
            "Profile 1",
            "https://profiles.example/v1",
            null,
            false,
            "chat",
            "embed",
            768,
            null,
            null,
            false
        );
        when(service.create(create)).thenReturn(response);
        when(service.list()).thenReturn(List.of(response));
        when(service.get("profile-1")).thenReturn(response);
        when(service.update("profile-1", update)).thenReturn(response);

        assertThat(controller.create(create)).isEqualTo(response);
        assertThat(controller.list()).containsExactly(response);
        assertThat(controller.get("profile-1")).isEqualTo(response);
        assertThat(controller.update("profile-1", update)).isEqualTo(response);
        ResponseEntity<Void> delete = controller.delete("profile-1");

        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.apiKeyMask()).isEqualTo("secr...-key");
        verify(service).delete("profile-1");
    }

    @Test
    void unsupportedModeUsesExistingProblemEnvelopeWithoutCallingService() throws Exception {
        AiProfileService service = mock(AiProfileService.class);
        org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new AiProfileController(service))
            .setControllerAdvice(new io.github.vfedoriv.graphrag.bootstrap.http.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/ai-profiles")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("""
                {"id":"profile","name":"Profile","baseUrl":"https://provider.invalid/v1",
                 "chatModel":"chat","embeddingModel":"embed","embeddingDimensions":768,
                 "structuredOutputMode":"UNKNOWN"}
                """))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON));
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private AiProfileResponse response(String id) {
        Instant now = Instant.parse("2026-06-18T12:00:00Z");
        return new AiProfileResponse(
            id,
            "Profile 1",
            "https://profiles.example/v1",
            "chat",
            "embed",
            768,
            60,
            2,
            false,
            1,
            true,
            "secr...-key",
            now,
            now
        );
    }
}
