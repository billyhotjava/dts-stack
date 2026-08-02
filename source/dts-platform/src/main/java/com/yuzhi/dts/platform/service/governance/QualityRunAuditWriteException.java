package com.yuzhi.dts.platform.service.governance;

final class QualityRunAuditWriteException extends RuntimeException {

    QualityRunAuditWriteException(RuntimeException cause) {
        super("质量运行审计写入失败", cause);
    }
}
