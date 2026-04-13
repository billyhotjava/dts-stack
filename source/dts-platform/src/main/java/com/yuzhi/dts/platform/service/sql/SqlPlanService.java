package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;

/** Filled in Sprint-11 F5 (T23). */
public interface SqlPlanService {
    PlanResultDto explain(ExplainRequest req);
}
