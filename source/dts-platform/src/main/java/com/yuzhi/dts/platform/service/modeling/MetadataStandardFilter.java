package com.yuzhi.dts.platform.service.modeling;

public class MetadataStandardFilter {

    private String keyword;
    private String domain;
    private String dataType;
    private String sourceSystem;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }
}
