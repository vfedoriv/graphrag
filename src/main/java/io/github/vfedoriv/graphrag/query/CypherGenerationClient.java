package io.github.vfedoriv.graphrag.query;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;

public interface CypherGenerationClient {

    GeneratedCypher generate(SchemaDocument schema, String prompt, int maxRows);
}
