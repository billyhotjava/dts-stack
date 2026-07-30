# Sprint-79 集成验收

本目录只接受真实运行证据，不以 source-only test、静态原型或占位截图替代 DTS 产品验收。

| ID | 场景 | 验收路径 | 关联 Task | 状态 |
|---|---|---|---|---|
| IT-01 | 工作台上下文 | 登录 → workbench → 切换七模块 → 刷新保持 plan/module/asset | F1/T01～T02 | PENDING |
| IT-02 | 业务维度与维度表 | 新建业务维度 → 属性 → CURRENT → 创建 DIMENSION → KEY 映射 | F2/T01～T02 | PENDING |
| IT-03 | 四类模型 | 用项目/财务 Demo 创建 FACT/SUMMARY/APPLICATION，完成逻辑保存门禁 | F2/T01～T03 | PENDING |
| IT-04 | 指标与关系 | 从模型字段创建/绑定指标 → 图中定位维度/标准/指标边 | F3/T01～T02 | PENDING |
| IT-05 | 发布短流程 | 模型页启动 Build Intent → Candidate 质量/审核边界可见 | F4/T01 | PENDING |
| IT-06 | DEV 物化 | 独立角色完成发布 → 生成/运行 binding → relation EXISTS → Catalog 资产可见 | F4/T02 | PENDING |
| IT-07 | 孤儿代码删除 | 无运行时 import、focused test、webapp build 通过 | F0/T03 | PASS |
| IT-08 | 旧入口退役 | 旧深链兼容、两版本零访问、删除后 404/重定向契约、回滚演练 | F5/T01～T02 | PENDING |

## 证据规范

每个 `IT-xx` 目录至少包含：

- `commands.md`：执行命令、时间、exit code；
- `api.json` 或 `db.txt`：脱敏后的真实响应/断言；
- UI 场景包含 `empty/loading/error/success` 适用状态截图；
- `result.md`：PASS/FAIL、commit、环境、遗留风险。

任何凭据、token、dbt profile、数据源 secret 不得进入证据目录。
