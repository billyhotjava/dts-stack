package com.yuzhi.dts.admin.service.ops;

import java.io.Serializable;
import java.time.Instant;

/**
 * 运维配置视图DTO，用于API返回，包含脱敏后的值
 */
public record OpsConfigView(
    Long id,
    String key,
    String value,          // 脱敏后的值
    String description,
    String category,
    boolean sensitive,
    String dataType,
    boolean editable,
    int sortOrder,
    String displayName,
    String scope,
    boolean restartRequired,
    String validationRule,
    String owner,
    String groupKey,
    String groupLabel,
    int groupOrder,
    Instant lastModified,
    String lastModifiedBy
) implements Serializable {}
