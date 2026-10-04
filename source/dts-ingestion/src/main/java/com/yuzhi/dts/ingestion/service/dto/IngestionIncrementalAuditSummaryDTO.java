package com.yuzhi.dts.ingestion.service.dto;

public class IngestionIncrementalAuditSummaryDTO {

    private long total;

    private long advanced;

    private long unchanged;

    private int advancedRate;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getAdvanced() {
        return advanced;
    }

    public void setAdvanced(long advanced) {
        this.advanced = advanced;
    }

    public long getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(long unchanged) {
        this.unchanged = unchanged;
    }

    public int getAdvancedRate() {
        return advancedRate;
    }

    public void setAdvancedRate(int advancedRate) {
        this.advancedRate = advancedRate;
    }
}
