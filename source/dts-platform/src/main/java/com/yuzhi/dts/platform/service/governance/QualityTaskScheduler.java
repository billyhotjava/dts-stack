package com.yuzhi.dts.platform.service.governance;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QualityTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(QualityTaskScheduler.class);

    private final QualityTaskService taskService;
    private final Environment environment;

    @Value("${dts.platform.governance.quality.task-scheduler-delay-ms:${dts.governance.quality.taskSchedulerDelayMs:60000}}")
    private long taskSchedulerDelayMs;

    public QualityTaskScheduler(QualityTaskService taskService, Environment environment) {
        this.taskService = taskService;
        this.environment = environment;
    }

    @PostConstruct
    void logConfig() {
        log.info(
            "[governance] quality task scheduler delay={}ms (resolved from dts.platform.governance.quality.task-scheduler-delay-ms, fallback dts.governance.quality.taskSchedulerDelayMs)",
            taskSchedulerDelayMs
        );
        boolean useLegacy = environment.containsProperty("dts.governance.quality.taskSchedulerDelayMs");
        boolean useNew = environment.containsProperty("dts.platform.governance.quality.task-scheduler-delay-ms");
        if (useLegacy && !useNew) {
            log.warn(
                "[governance] deprecated key in use: dts.governance.quality.taskSchedulerDelayMs, please migrate to dts.platform.governance.quality.task-scheduler-delay-ms"
            );
        }
    }

    @Scheduled(fixedDelayString = "${dts.platform.governance.quality.task-scheduler-delay-ms:${dts.governance.quality.taskSchedulerDelayMs:60000}}")
    public void tick() {
        taskService.runDueTasks();
    }
}
