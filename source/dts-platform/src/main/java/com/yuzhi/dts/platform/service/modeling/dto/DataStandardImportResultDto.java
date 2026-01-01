package com.yuzhi.dts.platform.service.modeling.dto;

import java.util.ArrayList;
import java.util.List;

public class DataStandardImportResultDto {

    private int totalRows;
    private int created;
    private int updated;
    private int skipped;
    private final List<String> errors = new ArrayList<>();

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        errors.add(message);
    }
}

