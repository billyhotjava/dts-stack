package com.yuzhi.dts.platform.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import tech.jhipster.async.ExceptionHandlingAsyncTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@Profile("!testdev & !testprod")
public class AsyncConfiguration implements AsyncConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfiguration.class);

    private final TaskExecutionProperties taskExecutionProperties;

    public AsyncConfiguration(TaskExecutionProperties taskExecutionProperties) {
        this.taskExecutionProperties = taskExecutionProperties;
    }

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        LOG.debug("Creating Async Task Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
        executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
        // When the queue and all threads are saturated, run the task on the caller thread
        // instead of rejecting it — prevents background jobs (incl. SQL exec workers, when
        // they migrate off ForkJoinPool) from being silently dropped and leaving downstream
        // state machines (e.g. QueryExecution rows) stuck in PENDING.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // BUG-C fix: wrap in DelegatingSecurityContextAsyncTaskExecutor so @Async tasks inherit
        // the calling thread's SecurityContext. Without this, audits emitted from background
        // workers fall back to anonymous → "_system:dts-platform" actor, masking the real user
        // who triggered the work.
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(new ExceptionHandlingAsyncTaskExecutor(executor));
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
