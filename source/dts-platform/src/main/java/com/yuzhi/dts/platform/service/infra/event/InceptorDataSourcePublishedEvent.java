package com.yuzhi.dts.platform.service.infra.event;

import com.yuzhi.dts.platform.service.infra.dto.InfraDataSourceDto;

public record InceptorDataSourcePublishedEvent(InfraDataSourceDto dataSource) {}
