package com.yuzhi.dts.platform.service.governance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QualityTaskScheduler {

    private final QualityTaskService taskService;

    public QualityTaskScheduler(QualityTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${dts.governance.quality.taskSchedulerDelayMs:60000}")
    public void tick() {
        taskService.runDueTasks();
    }
}

