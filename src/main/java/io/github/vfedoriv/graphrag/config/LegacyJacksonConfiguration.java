package io.github.vfedoriv.graphrag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared Jackson 2 mapper for components that still use the legacy Jackson API. */
@Configuration(proxyBeanMethods = false)
public class LegacyJacksonConfiguration {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper();
    }
}
