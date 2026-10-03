package io.github.vfedoriv.graphrag.ai.models;

/** Invalidates cached provider constructions when profile state changes. */
public interface AiModelCache { void invalidate(String profileId); }
