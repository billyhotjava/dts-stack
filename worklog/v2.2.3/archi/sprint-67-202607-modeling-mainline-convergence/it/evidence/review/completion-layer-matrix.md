# Sprint-67 完成层次矩阵

| 层次 | 状态 | 核心证据 | 边界 |
|---|---|---|---|
| 功能/契约 | DONE | 无 objectId 的 ModelSpec、双起点、四类表、标准/指标/发布交接、旧写冻结 | 外部运行提交受部署开关限制 |
| 自动化测试 | DONE | 最终新增后端定点 25/25；既有 Task evidence 按契约/API/迁移/前端分类 | 权限为真实 Spring Security 测试主体，不冒充生产账号 |
| 前端构建 | DONE | dts-platform-webapp production build，10587 modules，2m2s | 仅保留 bundle 大小与 caniuse-lite 提示 |
| 数据迁移 | DONE（受控迁移） | dry-run、checksum、幂等、租户隔离、旧写 410、NO-DROP | 物理删表未批准且不属于 DONE 声明 |
| 部署运行 | DONE（主线） | 最终候选容器健康、管理健康端点 200、真实 run 记录 | `RUNTIME_DISABLED`，不声明 Airflow/dbt 成功提交 |
| 浏览器 | DONE | 真实 Chrome95 A/B/C 3/3；desktop/narrow；精确失败恢复 1/1 | 失败恢复为 UI 注入，后端门禁另有 Java 测试 |
| 回滚 | DONE | Sprint 前镜像与最终候选双向切换各 58 秒，DB 对账一致 | 不恢复旧写、不执行数据库降级 |
| 物理退役 | NO-GO | consumer/usage/exit-gate 证据 | 等待零消费者、冲突归零和备份审批 |

Sprint-67 的 DONE 表示新主线可以受控发布、旧写已冻结且旧读兼容可回滚；不等于旧表已经物理删除，也不等于当前禁用的外部运行器已成功执行。
