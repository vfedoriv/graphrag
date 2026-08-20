# Schema JSON format

Schemas are authored as JSON, parsed into `SchemaDocument`, validated, and stored as immutable identities. This representative example exercises nodes, relationship properties, scalar/compound keys, ordinary indexes, and a vector index:

```json
{
  "name": "legal-contracts",
  "version": 2,
  "description": "Contracts, parties, and obligations",
  "nodes": [
    {
      "label": "Contract",
      "description": "A signed agreement",
      "key": ["contractId"],
      "properties": [
        {"name": "contractId", "type": "string", "required": true},
        {"name": "title", "type": "string", "required": false}
      ]
    },
    {
      "label": "Party",
      "description": "A legal or natural party",
      "key": ["name"],
      "properties": [
        {"name": "name", "type": "string", "required": true}
      ]
    }
  ],
  "relationships": [
    {
      "type": "HAS_PARTY",
      "from": "Contract",
      "to": "Party",
      "description": "Associates a party with a contract",
      "properties": [
        {"name": "role", "type": "string", "required": false}
      ]
    }
  ],
  "indexes": [
    {"label": "Contract", "properties": ["contractId"], "unique": true}
  ],
  "vectorIndexes": [
    {
      "name": "document_chunk_embedding",
      "label": "DocumentChunk",
      "property": "embedding",
      "dimensions": 1536,
      "similarity": "cosine"
    }
  ]
}
```

## Fields and rules

- Top level: required logical `name`, positive integer `version`, optional `description`, and collections of node, relationship, ordinary-index, and vector-index definitions.
- Node: `label`, optional description, one or more `key` property names (a single JSON string is accepted for compatibility), and property definitions.
- Relationship: `type`, `from`/`to` node labels, optional description, and optional properties.
- Property: `name`, supported scalar `type`, and `required` flag.
- Index: existing node label plus one or more existing properties; `unique` expresses uniqueness intent.
- Vector index: name, label, property, positive dimensions, and supported similarity.

Validation requires unique, syntactically safe labels/types/property names, valid endpoints and key/index property references, and coherent index definitions. Extraction and query validation use only the active schema's declared labels, relationship types, and properties.

## Version and registry behavior

`name + version` is immutable after save. An inactive record's JSON may be replaced only if that identity stays the same. Active schemas cannot be updated/deleted. Creating, generating, attaching, draft publication, and activation are distinct actions; see the [registry workflow](../workflows/schemas.md).

The vector index declaration is domain schema input, but document-chunk embedding compatibility is also guarded by the knowledge base's profile/provider/model/dimensions/resolved tokenizer. Use OpenAPI for the exact supported property-type/similarity enums in the running version.
