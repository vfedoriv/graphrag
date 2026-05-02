package io.github.vfedoriv.graphrag;

import org.springframework.boot.SpringApplication;

public class TestGraphragApplication {

    public static void main(String[] args) {
        SpringApplication.from(GraphragApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
