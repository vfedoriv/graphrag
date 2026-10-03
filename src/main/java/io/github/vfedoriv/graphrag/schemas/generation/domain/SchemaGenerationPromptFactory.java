package io.github.vfedoriv.graphrag.schemas.generation.domain;

public final class SchemaGenerationPromptFactory {

    public String schemaContract() {
        return """
            Schema key contract:
            - For each generated node, `key` can be either a single property name or a list of property names.
            - Every key component MUST exactly match a property name declared in that same node's properties.
            - In extraction output, include `key` inside head_properties/tail_properties as a comma-separated list
              of preferred key property names (e.g. "manufacturer,productName"), using only property names present in that node.
            - Avoid generic `id` unless `id` is explicitly present in that node's properties list.
            - Prefer meaningful canonical identity keys:
              Product: manufacturer + productName
              Model: manufacturer + modelCode
              SparePart: manufacturer + partNumber
              DocumentSection: documentId + sectionNumber or sectionTitle
              Location: country + state + city + street
              Person: fullName + birthDate
            - If multiple properties are needed for uniqueness, emit `key` as a list preserving deterministic order.
            """;
    }

    public String examplePrompt(String text, String userPrompt) {
        String userInstruction = userPrompt == null || userPrompt.isBlank()
            ? ""
            : "\nAdditional guidance:\n" + userPrompt;
        return """
            You generate examples for graph extraction.
            Return only a JSON array where each object has keys 'head', 'head_type', 'relation', 'tail', and 'tail_type'.
            Do not wrap with any outer object (no {"example": ...}).
            No markdown and no explanations.

            Generate representative entity-relationship examples from this text so they can guide schema extraction.
            %s

            Text:
            %s
            """.formatted(userInstruction, text);
    }
}
