package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.documents.application.processing.ChunkingService;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.ChunkReprocessingSelection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChunkMigrationContractsTest {

    @Test
    void snapshotsCanonicalDocumentHashesAndRestoresTheExactChunkTarget() {
        RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
        when(runtimeSettings.chunking()).thenReturn(settings());
        when(runtimeSettings.effectiveChunkerRevision()).thenReturn("chunker_" + "a".repeat(64));
        ChunkingService chunkingService = new ChunkingService(runtimeSettings);
        AiProfileNode profile = profile();
        ChunkingContext documentContext = chunkingService.snapshot(profile, "text");
        ChunkMigrationSnapshot.ChunkTarget chunkTarget = chunkingService.snapshotTarget(profile);
        Map<String, ChunkMigrationSnapshot.DocumentTarget> documents = new LinkedHashMap<>();
        documents.put("doc-b", documentTarget("b".repeat(64), documentContext));
        documents.put("doc-a", documentTarget("a".repeat(64), documentContext));
        ChunkMigrationSnapshot snapshot = new ChunkMigrationSnapshot(
            chunkingService.migrationTargetRevision(profile),
            ChunkReprocessingSelection.DOCUMENT_IDS,
            chunkTarget,
            profile.getId(),
            profile.getRevision(),
            "es-target",
            "schema-1",
            "c".repeat(64),
            documents
        );
        SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(new ObjectMapper());

        String canonical = jsonSupport.canonical(snapshot);
        ChunkMigrationSnapshot restoredSnapshot = jsonSupport.read(canonical, ChunkMigrationSnapshot.class);
        ChunkingContext restored = chunkingService.restore(
            profile,
            restoredSnapshot.chunkTarget(),
            restoredSnapshot.documents().get("doc-a")
        );

        assertThat(canonical.indexOf("\"doc-a\"")).isLessThan(canonical.indexOf("\"doc-b\""));
        assertThat(restored.effectiveRevision()).isEqualTo(documentContext.effectiveRevision());
        assertThat(restoredSnapshot.documents().get("doc-a").sourceSha256()).isEqualTo("a".repeat(64));
    }

    @Test
    void rejectsAChangedTokenizerTarget() {
        RuntimeSettingsService runtimeSettings = mock(RuntimeSettingsService.class);
        when(runtimeSettings.chunking()).thenReturn(settings());
        ChunkingService chunkingService = new ChunkingService(runtimeSettings);
        AiProfileNode profile = profile();
        ChunkingContext documentContext = chunkingService.snapshot(profile, "text");
        ChunkMigrationSnapshot.ChunkTarget current = chunkingService.snapshotTarget(profile);
        ChunkMigrationSnapshot.ChunkTarget changed = new ChunkMigrationSnapshot.ChunkTarget(
            current.strategyName(),
            current.strategyRevision(),
            current.targetTokens(),
            current.overlapTokens(),
            current.hardCharacterLimit(),
            current.parentTargetTokens(),
            current.parentHardCharacterLimit(),
            current.parentMaxPages(),
            current.contextHeaderMaxTokens(),
            current.contextHeaderMaxCharacters(),
            current.tokenizerId(),
            "changed-tokenizer-revision",
            current.tokenCountMode(),
            current.representationRevision(),
            current.settingsHash()
        );

        assertThatThrownBy(() -> chunkingService.restore(
            profile,
            changed,
            documentTarget("a".repeat(64), documentContext)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tokenizer target");
    }

    private ChunkMigrationSnapshot.DocumentTarget documentTarget(
        String sourceSha256,
        ChunkingContext context
    ) {
        return new ChunkMigrationSnapshot.DocumentTarget(
            sourceSha256,
            "text",
            context.parserRevision(),
            "TXT",
            context.effectiveRevision().value(),
            Map.of("preserveLineBreaks", true)
        );
    }

    private RuntimeSettingsService.ChunkingSettings settings() {
        return new RuntimeSettingsService.ChunkingSettings(
            "recursive",
            800,
            80,
            4000,
            1600,
            8000,
            2,
            64,
            256,
            "representation-v1"
        );
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setRevision(7);
        profile.setEmbeddingModel("unknown-local-model");
        return profile;
    }
}
