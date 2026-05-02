Let's think/create a plan for implementing hybrid/GraphRag knowledge base

It should have modules that allows:
- create graph database using predefined schema
- create graph database using schema that created on base of uploaded document domain/relationships
- upload documents (different types) into knowledge base
- parse those documents and create embeddings/vectors for them
- create nodes/relations for uploaded document in graph database
- generate queries to database based on using user prompts and AI
- validate those generated queries (are they valid Cypher queries)
- execute those queries and return query results
  It should follow typical Java enterprise app structure (controllers/services/repositories) and implement/expose REST API

Notes:
- we should not hardcode schema elements names/relations in code, consider to store chema separately (what format do you propose?) and load/use/verify this model/it's elements/properties when we use it in code
- when I upload document into knowledge base I want as result have:
  a) record about document upload (document name, document size, document type, document hash, link on document content (binary), upload date)
  b) vectors created afrer I generate embeddings for this document
  c) graph nodes and relations that corresponds to document context How I can achive this and how I can store this data
- we should have unit and integration tests that cover application functionality

Application stack:
Java 25, Spring Boot 4.0.6, LangChain and Langchain extensions (versions 1.14.0 and 1.14.0-beta24), Apache Tika,
Neo4J database, Docker compose ; OpenAI compatible LLM models;