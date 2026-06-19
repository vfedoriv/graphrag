package io.github.vfedoriv.graphrag.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.vfedoriv.graphrag.dto.RuntimeSettingResponse;
import io.github.vfedoriv.graphrag.dto.RuntimeSettingUpdateRequest;
import io.github.vfedoriv.graphrag.error.GlobalExceptionHandler;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuntimeSettingsControllerTest {

    private RuntimeSettingsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RuntimeSettingsService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RuntimeSettingsController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void listsMutableReadOnlyAndSensitiveSettings() throws Exception {
        when(service.list()).thenReturn(List.of(
            setting("app.query.max-rows", 25, false, true, "live"),
            setting("spring.neo4j.uri", "bolt://localhost:7687", false, false, "restart-required"),
            setting("app.model.api-key", Map.of("configured", true, "masked", true), true, false, "sensitive-read-only")
        ));

        mockMvc.perform(get("/api/v1/runtime-settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("app.query.max-rows"))
            .andExpect(jsonPath("$[0].updateMode").value("live"))
            .andExpect(jsonPath("$[0].activeValue").value(25))
            .andExpect(jsonPath("$[0].lifecycleState").value("active"))
            .andExpect(jsonPath("$[1].updateMode").value("restart-required"))
            .andExpect(jsonPath("$[2].sensitive").value(true))
            .andExpect(jsonPath("$[2].currentValue.masked").value(true));
    }

    @Test
    void updatesMutableRestartRequiredSettingWithPendingMetadata() throws Exception {
        when(service.update("app.storage.documents-root", "var/other-documents")).thenReturn(
            new RuntimeSettingResponse(
                "app.storage.documents-root",
                "storage",
                "string",
                "var/other-documents",
                "var/documents",
                "var/documents",
                "override",
                "pending-restart",
                true,
                false,
                false,
                Map.of("minLength", 1),
                "restart-required",
                "Document storage root changes are persisted for the next backend restart.",
                "Documents root",
                null
            )
        );

        mockMvc.perform(put("/api/v1/runtime-settings/{key}", "app.storage.documents-root")
                .contentType("application/json")
                .content("{\"value\":\"var/other-documents\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentValue").value("var/other-documents"))
            .andExpect(jsonPath("$.activeValue").value("var/documents"))
            .andExpect(jsonPath("$.lifecycleState").value("pending-restart"))
            .andExpect(jsonPath("$.updateMode").value("restart-required"))
            .andExpect(jsonPath("$.liveApplied").value(false));
    }

    @Test
    void updatesMutableSettingAndRejectsReadOnlyClearAndUnknownUpdate() throws Exception {
        when(service.update("app.query.max-rows", 25)).thenReturn(setting("app.query.max-rows", 25, false, true, "live"));
        when(service.update("spring.neo4j.uri", "bolt://other"))
            .thenThrow(new IllegalArgumentException("Runtime setting spring.neo4j.uri cannot be changed through the runtime settings API because it is restart-required"));
        when(service.clear("spring.neo4j.uri"))
            .thenThrow(new IllegalArgumentException("Runtime setting spring.neo4j.uri cannot be changed through the runtime settings API because it is restart-required"));
        when(service.update("spring.neo4j.pool.max-connection-pool-size", 10))
            .thenThrow(new IllegalArgumentException("Runtime setting is not allowlisted: spring.neo4j.pool.max-connection-pool-size"));

        mockMvc.perform(put("/api/v1/runtime-settings/{key}", "app.query.max-rows")
                .contentType("application/json")
                .content("{\"value\":25}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentValue").value(25));

        mockMvc.perform(put("/api/v1/runtime-settings/{key}", "spring.neo4j.uri")
                .contentType("application/json")
                .content("{\"value\":\"bolt://other\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Runtime setting spring.neo4j.uri cannot be changed through the runtime settings API because it is restart-required"));

        mockMvc.perform(delete("/api/v1/runtime-settings/{key}", "spring.neo4j.uri"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/runtime-settings/{key}", "spring.neo4j.pool.max-connection-pool-size")
                .contentType("application/json")
                .content("{\"value\":10}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Runtime setting is not allowlisted: spring.neo4j.pool.max-connection-pool-size"));
    }

    @Test
    void bulkUpdatesMutableSettingsAndReturnsUpdatedSettings() throws Exception {
        when(service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.storage.documents-root", "var/bulk-documents")
        ))).thenReturn(List.of(
            setting("app.query.max-rows", 25, false, true, "live"),
            new RuntimeSettingResponse(
                "app.storage.documents-root",
                "storage",
                "string",
                "var/bulk-documents",
                "var/documents",
                "var/documents",
                "override",
                "pending-restart",
                true,
                false,
                false,
                Map.of("minLength", 1),
                "restart-required",
                "Document storage root changes are persisted for the next backend restart.",
                "Documents root",
                null
            )
        ));

        mockMvc.perform(put("/api/v1/runtime-settings")
                .contentType("application/json")
                .content("""
                    {
                      "updates": [
                        {"key": "app.query.max-rows", "value": 25},
                        {"key": "app.storage.documents-root", "value": "var/bulk-documents"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("app.query.max-rows"))
            .andExpect(jsonPath("$[0].currentValue").value(25))
            .andExpect(jsonPath("$[1].key").value("app.storage.documents-root"))
            .andExpect(jsonPath("$[1].currentValue").value("var/bulk-documents"))
            .andExpect(jsonPath("$[1].lifecycleState").value("pending-restart"));
    }

    @Test
    void bulkUpdateRejectsInvalidRequests() throws Exception {
        when(service.update(List.of(
            new RuntimeSettingUpdateRequest("app.query.max-rows", 25),
            new RuntimeSettingUpdateRequest("app.query.max-rows", 30)
        ))).thenThrow(new IllegalArgumentException("Bulk runtime setting update contains duplicate key: app.query.max-rows"));

        mockMvc.perform(put("/api/v1/runtime-settings")
                .contentType("application/json")
                .content("{\"updates\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.updates").exists());

        mockMvc.perform(put("/api/v1/runtime-settings")
                .contentType("application/json")
                .content("{\"updates\":[{\"key\":\"\",\"value\":25}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors['updates[0].key']").exists());

        mockMvc.perform(put("/api/v1/runtime-settings")
                .contentType("application/json")
                .content("{\"updates\":[{\"key\":\"app.query.max-rows\"}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors['updates[0].value']").exists());

        mockMvc.perform(put("/api/v1/runtime-settings")
                .contentType("application/json")
                .content("""
                    {
                      "updates": [
                        {"key": "app.query.max-rows", "value": 25},
                        {"key": "app.query.max-rows", "value": 30}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Bulk runtime setting update contains duplicate key: app.query.max-rows"));
    }

    private RuntimeSettingResponse setting(String key, Object value, boolean sensitive, boolean liveApplied, String updateMode) {
        return new RuntimeSettingResponse(
            key,
            "test",
            "string",
            value,
            value,
            value,
            "default",
            liveApplied ? "active" : "default",
            liveApplied,
            liveApplied,
            sensitive,
            Map.of(),
            updateMode,
            updateMode,
            key,
            null
        );
    }
}
