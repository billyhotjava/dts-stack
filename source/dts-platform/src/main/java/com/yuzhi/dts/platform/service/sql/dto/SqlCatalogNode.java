package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;

public record SqlCatalogNode(String id, String label, SqlCatalogNodeType type, List<SqlCatalogNode> children) {}
