package com.yuzhi.dts.opmanager.packageinfo;

public record PackageRegistration(String id, String sourcePath, String registeredAt, PackageValidationResult validation) {}
