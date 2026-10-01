package io.github.vfedoriv.graphrag.documents.domain.chunking;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import java.util.List;

public interface ChunkingStrategy {

    String name();

    String revision();

    List<ChunkSlice> split(ParsedSection section, ChunkingContext context);
}
