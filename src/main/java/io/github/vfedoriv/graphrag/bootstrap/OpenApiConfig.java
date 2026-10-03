package io.github.vfedoriv.graphrag.bootstrap;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI graphragOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("GraphRAG API")
                .version("v1")
                .description("REST API for schema-managed GraphRAG workflows.")
                .contact(new Contact().name("GraphRAG")));
    }
}
