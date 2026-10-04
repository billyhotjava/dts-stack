package com.yuzhi.dts.opmanager.runtime;

public record DockerContainer(String id, String name, String image, String state, String status) {}
