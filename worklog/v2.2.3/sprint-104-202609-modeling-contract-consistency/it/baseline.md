# 交付基线（G0）

**登记日期**：2026-09-06  
**结论**：当前测试环境与独立样例基线已建立；Chrome 95 和完整模型端到端验收仍待执行。
**责任**：F1/T01。按用户要求只建一个 Feature，基线作为其独立前置 task。

| 探针 | 当前状态 | 证据/未决项 |
|---|---|---|
| P1 可运行实例 | 已验证，当前版本发布进行中 | https://bi.yuzhicloud.com；Compose deploy，配置 /opt/prod/s10/deploy/docker-compose-app.yml |
| P2 登录 | PASS | 现有 Chrome 登录会话完成分类、业务过程、集市、主题域与文件接入创建 |
| P3 迁移/表结构 | PASS | 模型/计划/实现/草稿表可读；databasechangelog 最新 20260904_02_model_release_candidate_visual_target_repair 已 EXECUTED；本 sprint 无新迁移 |
| P4 代表性数据 | 独立样例 PASS | 用户授权新建测试样例，3 行4个输入字段，总金额350；见 evidence/current-environment/sample-initialization.md |
| P5 API 走查 | 前置流程 PASS；模型流程待验 | 页面已成功调用正式创建、上传封存、接入执行、目录采集与密级维护入口 |
| P6 UI harness | 未验证 | Chrome 95、1366×768/窄屏、截图路径待确认 |
| P7 构建/测试 | PASS | Node契约40/40；部署目录Vitest92/92；Docker Maven99/99；正式两镜像及OpManager包完成（bd0670acc）；见 source-test-summary 与 release-plan |
| P8 外部依赖 | dbt/PostgreSQL与接入编排 PASS；发布待验 | 正式dbt镜像5例真实检测通过；文件接入Airflow SUCCESS；标准证据/模型质量发布仍待验 |

## 开工约束

- T01 完成探测后记录命令、日期、目标环境、退出状态和脱敏结果。
- 关键探针不通过则明确影响 task；修复基线不暗含新增部署权限。
- 只有相关基线与 DoR 通过，Feature/Task 才能从 DRAFT 改为 READY。用户已授权先修正文档并开始编码：已固定源码契约的子项可登记 IN_PROGRESS；现场探针仍保留 GAP，不据此宣称 Feature READY 或运行验收通过。
- 不写入或删除用户已有 PRJDEMO 数据；现场账号凭据不入文档。

