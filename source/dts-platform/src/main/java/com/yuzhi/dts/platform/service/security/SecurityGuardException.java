package com.yuzhi.dts.platform.service.security;

/**
 * 在执行 SQL 语句前进行安全守卫时抛出的异常，用于阻止潜在的越权访问。
 */
public class SecurityGuardException extends RuntimeException {

    public SecurityGuardException(String message) {
        super(message);
    }
}
