package com.yuzhi.dts.platform.service.modeling.dto;

import java.util.ArrayList;
import java.util.List;

public class MetadataStandardImportResultDto {

    private int totalRows;
    private int created;
    private int updated;
    private int skipped;
    private List<String> errors;

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

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addError(String message) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(message);
    }
}
