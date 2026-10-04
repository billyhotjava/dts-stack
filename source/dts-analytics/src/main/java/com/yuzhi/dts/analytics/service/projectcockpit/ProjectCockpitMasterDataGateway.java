package com.yuzhi.dts.analytics.service.projectcockpit;

import org.springframework.stereotype.Component;

@Component
public class ProjectCockpitMasterDataGateway {

    public ProjectCockpitMasterDataState currentState() {
        return new ProjectCockpitMasterDataState(
                false,
                "未接入",
                "项目主数据系统暂未建立，当前项目、子项目和节点基础信息由项目主体域数仓维表承载。后续主数据系统就绪后，可在此接口替换为正式主数据来源。");
    }

    public record ProjectCockpitMasterDataState(boolean connected, String status, String message) {}
}
