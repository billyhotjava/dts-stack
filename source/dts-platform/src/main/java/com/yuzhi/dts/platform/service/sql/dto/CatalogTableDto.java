package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record CatalogTableDto(String name, String type, String comment, Long rowCountEstimate) implements Serializable {}
