package io.github.vfedoriv.graphrag.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SchemaDraftPublicationConfiguration {
    @Bean("schemaDraftEvaluationExecutor")
    public ThreadPoolTaskExecutor schemaDraftEvaluationExecutor(SchemaDraftEvaluationProperties properties) {
        return executor("schema-draft-evaluation-", properties.concurrency(), properties.queueCapacity());
    }

    private ThreadPoolTaskExecutor executor(String prefix, int concurrency, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
