package io.github.vfedoriv.graphrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GraphragApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphragApplication.class, args);
    }

}
