package com.yuzhi.dts.ingestion.service.etl.rollback;

public enum RollbackLevel {
	TRUNCATE_DATA(1),
	REBUILD_SCHEMA(2),
	FULL_CASCADE(3);

	private final int code;

	RollbackLevel(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}

	public static RollbackLevel fromCode(int code) {
		for (RollbackLevel l : values()) {
			if (l.code == code) return l;
		}
		throw new IllegalArgumentException("Unknown rollback level: " + code);
	}
}
