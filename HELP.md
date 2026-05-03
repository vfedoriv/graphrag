# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.6/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.6/maven-plugin/build-image.html)
* [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.6/reference/testing/testcontainers.html#testing.testcontainers)
* [Testcontainers Neo4j Module Reference Guide](https://java.testcontainers.org/modules/databases/neo4j/)
* [Testcontainers Postgres Module Reference Guide](https://java.testcontainers.org/modules/databases/postgres/)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.6/reference/actuator/index.html)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/4.0.6/specification/configuration-metadata/annotation-processor.html)
* [Spring Data Neo4j](https://docs.spring.io/spring-boot/4.0.6/reference/data/nosql.html#data.nosql.neo4j)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.6/reference/using/devtools.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.6/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Neo4j](https://docs.spring.io/spring-boot/4.0.6/reference/data/nosql.html#data.nosql.neo4j)
* [OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
* [Tika Document Reader](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html#_tika_docx_pptx_html)
* [Neo4j Vector Database](https://docs.spring.io/spring-ai/reference/api/vectordbs/neo4j.html)
* [Testcontainers](https://java.testcontainers.org/)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.6/reference/web/servlet.html)
* [LangChain4j](https://docs.langchain4j.dev/)

### Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Accessing Data with Neo4j](https://spring.io/guides/gs/accessing-data-neo4j/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Langchain4j Neo4J Integration](https://docs.langchain4j.dev/integrations/embedding-stores/neo4j)
* [Integrating Neo4j With LangChain4j for GraphRAG Vector Stores and Retrievers](https://neo4j.com/blog/developer/langchain4j-graphrag-vector-stores-retrievers/)

### Docker Compose support

This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

* neo4j: [`neo4j:5.26.25`](https://hub.docker.com/_/neo4j)


Please review the tags of the used images and set them to the same as you're running in production.

### Testcontainers support

This project
uses [Testcontainers at development time](https://docs.spring.io/spring-boot/4.0.6/reference/features/dev-services.html#features.dev-services.testcontainers).

Testcontainers has been configured to use the following Docker images:

* [`neo4j:5.26.25`](https://hub.docker.com/_/neo4j)


Please review the tags of the used images and set them to the same as you're running in production.

### Docker Compose smoke test

Use these commands to run a quick local smoke test against Neo4j from `compose.yaml`:

1. `docker compose up -d neo4j`
2. `./mvnw test`
3. `docker compose down -v`

Optional focused run for the synchronous end-to-end MVP flow test:

`./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test`

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
