package com.yuzhi.dts.platform.service.sql.dto;

import java.io.Serializable;

public record CatalogSchemaDto(String name, String catalog) implements Serializable {}
