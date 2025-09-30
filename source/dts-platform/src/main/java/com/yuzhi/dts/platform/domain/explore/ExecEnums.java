package com.yuzhi.dts.platform.domain.explore;

public final class ExecEnums {
    private ExecEnums() {}

    public enum SecurityLevel { PUBLIC, INTERNAL, SECRET, TOP_SECRET }
    public enum DataSourceType { HIVE, TRINO, JDBC }
    public enum ExecEngine { TRINO, HIVE }
    public enum ExecStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELED }
    public enum VizType { TABLE, LINE, BAR, PIE, AREA, SCATTER }
}

