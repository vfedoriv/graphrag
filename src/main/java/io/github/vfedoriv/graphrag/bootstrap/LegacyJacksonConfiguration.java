package io.github.vfedoriv.graphrag.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared Jackson 2 mapper for components that still use the legacy Jackson API. */
@Configuration(proxyBeanMethods = false)
public class LegacyJacksonConfiguration {

    @Bean
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
