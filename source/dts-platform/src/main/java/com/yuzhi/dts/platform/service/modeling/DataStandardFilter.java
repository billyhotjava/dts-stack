package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;

public class DataStandardFilter {

    private String keyword;
    private String domain;
    private DataStandardStatus status;
    private DataSecurityLevel securityLevel;

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

    public DataStandardStatus getStatus() {
        return status;
    }

    public void setStatus(DataStandardStatus status) {
        this.status = status;
    }

    public DataSecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(DataSecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }
}

