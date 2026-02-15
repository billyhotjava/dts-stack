package com.yuzhi.dts.ingestion.service.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IngestionExecutionObservabilityDTO {

    private Long taskId;
    private String sourceType;
    private java.util.UUID sourceDataSourceId;
    private Instant windowStart;
    private Instant windowEnd;
    private Integer windowDays;
    private Integer timeoutMinutes;

    private long total;
    private long success;
    private long failed;
    private long running;
    private long terminal;
    private long timeout;

    private Double successRate;
    private Double timeoutRate;
    private Double avgDurationSeconds;
    private Double mttrSeconds;

    private List<FailureTopItem> failureTop = new ArrayList<>();
    private List<TrendItem> trend = new ArrayList<>();

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public java.util.UUID getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(java.util.UUID sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(Integer timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getRunning() {
        return running;
    }

    public void setRunning(long running) {
        this.running = running;
    }

    public long getTerminal() {
        return terminal;
    }

    public void setTerminal(long terminal) {
        this.terminal = terminal;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getTimeoutRate() {
        return timeoutRate;
    }

    public void setTimeoutRate(Double timeoutRate) {
        this.timeoutRate = timeoutRate;
    }

    public Double getAvgDurationSeconds() {
        return avgDurationSeconds;
    }

    public void setAvgDurationSeconds(Double avgDurationSeconds) {
        this.avgDurationSeconds = avgDurationSeconds;
    }

    public Double getMttrSeconds() {
        return mttrSeconds;
    }

    public void setMttrSeconds(Double mttrSeconds) {
        this.mttrSeconds = mttrSeconds;
    }

    public List<FailureTopItem> getFailureTop() {
        return failureTop;
    }

    public void setFailureTop(List<FailureTopItem> failureTop) {
        this.failureTop = failureTop;
    }

    public List<TrendItem> getTrend() {
        return trend;
    }

    public void setTrend(List<TrendItem> trend) {
        this.trend = trend;
    }

    public static class FailureTopItem {
        private String category;
        private long count;

        public FailureTopItem() {}

        public FailureTopItem(String category, long count) {
            this.category = category;
            this.count = count;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public static class TrendItem {
        private String day;
        private long total;
        private long success;
        private long failed;
        private long timeout;

        public TrendItem() {}

        public TrendItem(String day, long total, long success, long failed, long timeout) {
            this.day = day;
            this.total = total;
            this.success = success;
            this.failed = failed;
            this.timeout = timeout;
        }

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getSuccess() {
            return success;
        }

        public void setSuccess(long success) {
            this.success = success;
        }

        public long getFailed() {
            return failed;
        }

        public void setFailed(long failed) {
            this.failed = failed;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }
}
