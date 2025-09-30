package com.yuzhi.dts.platform.web.rest;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/keycloak/localization")
public class KeycloakLocalizationResource {

    @GetMapping("/zh-CN")
    public Map<String, Object> zhCN() {
        return Map.of(
            "userManagement",
            Map.of("title", "用户管理", "create", "创建用户", "delete", "删除用户"),
            "roleManagement",
            Map.of("title", "角色管理", "assign", "分配角色"),
            "groupManagement",
            Map.of("title", "用户组管理"),
            "commonActions",
            Map.of("save", "保存", "cancel", "取消"),
            "statusMessages",
            Map.of("success", "操作成功", "error", "操作失败"),
            "formLabels",
            Map.of("username", "用户名", "email", "邮箱"),
            "pagination",
            Map.of("prev", "上一页", "next", "下一页")
        );
    }
}

