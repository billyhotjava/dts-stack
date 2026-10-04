package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IngestionGovernanceOverviewDTO {

    private Instant generatedAt;
    private long running;
    private long preparing;
    private long queueLength;
    private long blockedByPolicy;
    private Double avgExecutionSeconds;
    private Double avgQueueWaitSeconds;
    private Double maxQueueWaitSeconds;
    private List<SourceLoadItem> sourceLoads = new ArrayList<>();
    private List<ProjectLoadItem> projectLoads = new ArrayList<>();

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public long getRunning() {
        return running;
    }

    public void setRunning(long running) {
        this.running = running;
    }

    public long getPreparing() {
        return preparing;
    }

    public void setPreparing(long preparing) {
        this.preparing = preparing;
    }

    public long getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(long queueLength) {
        this.queueLength = queueLength;
    }

    public long getBlockedByPolicy() {
        return blockedByPolicy;
    }

    public void setBlockedByPolicy(long blockedByPolicy) {
        this.blockedByPolicy = blockedByPolicy;
    }

    public Double getAvgExecutionSeconds() {
        return avgExecutionSeconds;
    }

    public void setAvgExecutionSeconds(Double avgExecutionSeconds) {
        this.avgExecutionSeconds = avgExecutionSeconds;
    }

    public Double getAvgQueueWaitSeconds() {
        return avgQueueWaitSeconds;
    }

    public void setAvgQueueWaitSeconds(Double avgQueueWaitSeconds) {
        this.avgQueueWaitSeconds = avgQueueWaitSeconds;
    }

    public Double getMaxQueueWaitSeconds() {
        return maxQueueWaitSeconds;
    }

    public void setMaxQueueWaitSeconds(Double maxQueueWaitSeconds) {
        this.maxQueueWaitSeconds = maxQueueWaitSeconds;
    }

    public List<SourceLoadItem> getSourceLoads() {
        return sourceLoads;
    }

    public void setSourceLoads(List<SourceLoadItem> sourceLoads) {
        this.sourceLoads = sourceLoads;
    }

    public List<ProjectLoadItem> getProjectLoads() {
        return projectLoads;
    }

    public void setProjectLoads(List<ProjectLoadItem> projectLoads) {
        this.projectLoads = projectLoads;
    }

    public static class SourceLoadItem {
        private UUID sourceDataSourceId;
        private String sourceType;
        private long running;
        private long preparing;

        public SourceLoadItem() {}

        public SourceLoadItem(UUID sourceDataSourceId, String sourceType, long running, long preparing) {
            this.sourceDataSourceId = sourceDataSourceId;
            this.sourceType = sourceType;
            this.running = running;
            this.preparing = preparing;
        }

        public UUID getSourceDataSourceId() {
            return sourceDataSourceId;
        }

        public void setSourceDataSourceId(UUID sourceDataSourceId) {
            this.sourceDataSourceId = sourceDataSourceId;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public long getRunning() {
            return running;
        }

        public void setRunning(long running) {
            this.running = running;
        }

        public long getPreparing() {
            return preparing;
        }

        public void setPreparing(long preparing) {
            this.preparing = preparing;
        }
    }

    public static class ProjectLoadItem {
        private String projectKey;
        private long running;
        private long preparing;

        public ProjectLoadItem() {}

        public ProjectLoadItem(String projectKey, long running, long preparing) {
            this.projectKey = projectKey;
            this.running = running;
            this.preparing = preparing;
        }

        public String getProjectKey() {
            return projectKey;
        }

        public void setProjectKey(String projectKey) {
            this.projectKey = projectKey;
        }

        public long getRunning() {
            return running;
        }

        public void setRunning(long running) {
            this.running = running;
        }

        public long getPreparing() {
            return preparing;
        }

        public void setPreparing(long preparing) {
            this.preparing = preparing;
        }
    }
}
