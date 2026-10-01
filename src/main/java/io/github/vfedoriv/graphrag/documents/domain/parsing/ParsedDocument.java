package io.github.vfedoriv.graphrag.documents.domain.parsing;

import java.util.List;
import java.util.Map;

public record ParsedDocument(
    String parserId,
    String format,
    List<ParsedSection> sections,
    Map<String, Object> metadata
) {
    public ParsedDocument {
        sections = sections == null ? List.of() : List.copyOf(sections);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String text() {
        return sections.stream()
            .map(ParsedSection::text)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("");
    }
}
