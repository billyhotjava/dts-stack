package com.yuzhi.dts.admin.domain.enumeration;

/**
 * 人员主数据来源类型。
 */
public enum PersonSourceType {
    API,
    EXCEL,
    MANUAL,
    MDM;

    public String getCode() {
        return name();
    }
}
