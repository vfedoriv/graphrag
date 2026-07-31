package io.github.vfedoriv.graphrag.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AdvancedSearchConfiguration {
    @Bean("advancedSearchRunExecutor")
    public ThreadPoolTaskExecutor advancedSearchRunExecutor(AdvancedSearchProperties properties) {
        return executor(properties.concurrency(), properties.queueCapacity(), "advanced-search-run-");
    }

    @Bean("advancedSearchBranchExecutor")
    public ThreadPoolTaskExecutor advancedSearchBranchExecutor(AdvancedSearchProperties properties) {
        return executor(properties.branchConcurrency(), properties.branchConcurrency(), "advanced-search-branch-");
    }

    private ThreadPoolTaskExecutor executor(int concurrency, int queueCapacity, String prefix) {
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
