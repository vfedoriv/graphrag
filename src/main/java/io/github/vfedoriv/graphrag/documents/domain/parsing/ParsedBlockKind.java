package io.github.vfedoriv.graphrag.documents.domain.parsing;

public enum ParsedBlockKind {
    PAGE,
    HEADING,
    PARAGRAPH,
    LINE,
    TABLE,
    TABLE_ROW,
    TABLE_CELL,
    TEXT
}
