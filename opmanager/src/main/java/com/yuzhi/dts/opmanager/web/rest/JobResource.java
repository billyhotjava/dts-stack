package com.yuzhi.dts.opmanager.web.rest;

import com.yuzhi.dts.opmanager.job.FileJobStore;
import com.yuzhi.dts.opmanager.job.JobEvent;
import com.yuzhi.dts.opmanager.job.PlanJobRequest;
import com.yuzhi.dts.opmanager.job.UpgradeJob;
import com.yuzhi.dts.opmanager.job.UpgradePlanningService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opmanager/jobs")
public class JobResource {

    private final FileJobStore jobStore;
    private final UpgradePlanningService planningService;

    public JobResource(FileJobStore jobStore, UpgradePlanningService planningService) {
        this.jobStore = jobStore;
        this.planningService = planningService;
    }

    @GetMapping
    public List<UpgradeJob> list() {
        return jobStore.list();
    }

    @PostMapping("/plan")
    public UpgradeJob plan(@Valid @RequestBody PlanJobRequest request) {
        String lookupId = request.lookupId();
        if (lookupId == null || lookupId.isBlank()) {
            throw new IllegalArgumentException("packageRegistrationId is required");
        }
        return planningService.createPlan(lookupId, request.note());
    }

    @GetMapping("/{id}")
    public UpgradeJob get(@PathVariable String id) {
        return jobStore.find(id).orElseThrow(() -> new NoSuchElementException("job not found"));
    }

    @GetMapping("/{id}/events")
    public List<JobEvent> events(@PathVariable String id) {
        if (jobStore.find(id).isEmpty()) {
            throw new NoSuchElementException("job not found");
        }
        return jobStore.events(id);
    }
}
