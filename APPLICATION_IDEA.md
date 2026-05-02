Let's think/create a plan for implementing hybrid/GraphRag knowledge base.

It should have modules that allow:
- create a graph database using predefined schema
- create a graph database using schema that is created on the base of uploaded document domain/relationships
- upload documents (different types) into the knowledge base
- parse those documents and create embeddings/vectors for them
- create nodes/relations for an uploaded document in a graph database
- generate queries to a database based on using user prompts and AI
- validate those generated queries (are they valid Cypher queries)
- to execute those queries and return query results, 
It should follow the typical Java enterprise app structure (controllers/services/repositories) and implement/expose REST API

Notes:
- we should not hardcode schema elements names/relations in code, consider storing schema separately (what format do you propose?) and load/use/verify this model/ its elements /properties when we use it in code
- when I upload a document into knowledge base, I want as a result have:
  a) record about document upload (document name, document size, document type, document hash, link on document content (binary), upload date)
  b) vectors created after I generate embeddings for this document
  c) graph nodes and relations that correspond to document context 
 Think what optimal way to store all this data
- we should have unit and integration tests that cover application functionality

At this moment we don't need any user authentication or authorization

Application stack:
Java 25, Spring Boot 4.0.6, LangChain and Langchain extensions (versions 1.14.0 and 1.14.0-beta24), Apache Tika,
Neo4J 5.26.25 database, Docker compose; OpenAI compatible LLM models;