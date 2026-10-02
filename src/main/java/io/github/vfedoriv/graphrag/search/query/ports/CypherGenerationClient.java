package io.github.vfedoriv.graphrag.search.query.ports;

import io.github.vfedoriv.graphrag.search.query.domain.GeneratedCypher;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;

public interface CypherGenerationClient {

    GeneratedCypher generate(SchemaDocument schema, String prompt, int maxRows);
}
