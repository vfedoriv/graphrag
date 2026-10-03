package io.github.vfedoriv.graphrag.settings.ports;


import io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.ChunkingSettings;

public interface ChunkRevisionInspection {
    String calculate(ChunkingSettings suppliedSettings);
}
