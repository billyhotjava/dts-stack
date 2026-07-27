package com.yuzhi.dts.platform.service.modeling;

/** The current-duty truth source could not produce an authoritative answer. */
public class ReleaseDutyDirectoryUnavailableException
    extends RuntimeException {

    public ReleaseDutyDirectoryUnavailableException(String message) {
        super(message);
    }

    public ReleaseDutyDirectoryUnavailableException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
