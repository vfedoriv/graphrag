package io.github.vfedoriv.graphrag.bootstrap.integration.settings;

import io.github.vfedoriv.graphrag.documents.contracts.DocumentChunkRevisions;
import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.ChunkingSettings;
import io.github.vfedoriv.graphrag.settings.ports.ChunkRevisionInspection;
import org.springframework.stereotype.Component;

@Component
public class SettingsChunkRevisionAdapter implements ChunkRevisionInspection {
    private final DocumentChunkRevisions documents;

    public SettingsChunkRevisionAdapter(DocumentChunkRevisions documents) {
        this.documents = documents;
    }

    @Override
    public String calculate(ChunkingSettings supplied) {
        return documents.calculate(new DocumentChunkRevisions.Settings(supplied.strategy(), supplied.targetTokens(),
            supplied.overlapTokens(), supplied.hardCharacterLimit(), supplied.parentTargetTokens(),
            supplied.parentHardCharacterLimit(), supplied.parentMaxPages(), supplied.contextHeaderMaxTokens(),
            supplied.contextHeaderMaxCharacters(), supplied.representationRevision()));
    }
}
