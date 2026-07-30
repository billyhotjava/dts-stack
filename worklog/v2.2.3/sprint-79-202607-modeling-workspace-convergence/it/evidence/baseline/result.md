# Baseline result

**结果**：PASS_WITH_GAPS  
**日期**：2026-07-30  
**环境**：本地 v2.2.3 Compose，正式入口 `https://bi.yuzhicloud.com`  
**浏览器**：Google Chrome `150.0.7871.128`

## 已通过

- 一次性、已审批测试身份可登录 DTS，认证端点不再返回 401。
- `/modeling/workbench` 显示 canonical `warehouse-plan-workbench`。
- `/modeling/models` 显示 canonical `model-center-page`。
- 两个页面无 page error、request failure 或 `/api/**` 4xx/5xx。
- 测试退出后 Keycloak 用户、管理快照和认证缓存均无残留。

## 尚未通过

- 当前环境不是 Chrome 95，兼容验收必须在 F1 完成后补。
- 四类 ModelSpec、implementation、ReleaseCandidate 代表数据尚未补齐。
- dbt/Airflow DEV 物化与生产门禁未由本用例验证。

本结果只解除 F1 壳层的认证基线阻塞，不解除 F2/F4 的代表数据和物化门禁。
