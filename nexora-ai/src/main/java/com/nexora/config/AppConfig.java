package com.nexora.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    /** Virtual threads (Java 21) run the streaming AI calls without blocking request threads. */
    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ExecutorService aiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
