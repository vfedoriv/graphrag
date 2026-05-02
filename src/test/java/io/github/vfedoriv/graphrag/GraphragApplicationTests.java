package io.github.vfedoriv.graphrag;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = GraphragApplicationTests.LightweightContext.class)
class GraphragApplicationTests {

    @Test
    void contextLoads() {
    }

    @Configuration(proxyBeanMethods = false)
    static class LightweightContext {
        @Bean
        String healthMarkerBean() {
            return "ok";
        }
    }
}
