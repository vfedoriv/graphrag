package io.github.vfedoriv.graphrag.document.chunking;

import io.github.vfedoriv.graphrag.document.ParsedSection;
import java.util.List;

public interface ChunkingStrategy {

    String name();

    String revision();

    List<ChunkSlice> split(ParsedSection section, ChunkingContext context);
}
