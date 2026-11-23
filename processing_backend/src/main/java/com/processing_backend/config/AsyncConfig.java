package com.processing_backend.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class AsyncConfig {

	@Bean(name = "documentJobExecutor")
	public Executor documentJobExecutor(AppProperties appProperties) {
		int concurrency = Math.max(1, appProperties.getJobs().getConcurrency());
		int queueCapacity = Math.max(1, appProperties.getJobs().getQueueCapacity());

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("doc-job-");
		executor.setCorePoolSize(concurrency);
		executor.setMaxPoolSize(concurrency);
		executor.setQueueCapacity(queueCapacity);
		executor.setAllowCoreThreadTimeOut(true);
		// Bounded: never create unbounded threads; reject when saturated.
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.initialize();
		return executor;
	}
}
