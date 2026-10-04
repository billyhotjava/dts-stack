package com.yuzhi.dts.admin.service.personnel;

public class PersonnelImportException extends RuntimeException {

    public PersonnelImportException(String message) {
        super(message);
    }

    public PersonnelImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
