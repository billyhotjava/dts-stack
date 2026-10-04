package com.yuzhi.dts.platform.web.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class DataStandardSettingsDto {

    @Min(1)
    private long maxFileSize;

    @NotEmpty
    private List<String> allowedExtensions;

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}

