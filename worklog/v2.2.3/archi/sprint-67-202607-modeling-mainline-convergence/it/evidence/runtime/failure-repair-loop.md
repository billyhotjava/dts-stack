# F6-T02 失败修复闭环证据

验证时间：2026-07-20 02:55 +08:00

## 已证明闭环

1. 模型详情以同一 `modelSpecId/revision/implementationMode` 进入 SQL/dbt 实现。
2. compile、test、review、publish 与外部注册均校验当前 revision/checksum，旧证据不能发布新 revision。
3. Catalog、BI dataset 与 lineage step 独立记录尝试、成功或失败，失败后按同一发布记录幂等重试。
4. 运维中的失败 `DBT_RUN` 保留 ModelSpec 引用，并返回同一模型和 revision 修复，不创建第二模型事实。

## Chromium 95 证据

- `../chrome95/f6-model-implementation-context-chromium95.png`：1366x768，从模型详情进入实现时保留精确 ModelSpec、r3 和 `DBT_MANAGED`。
- `../chrome95/f6-model-repair-chromium95-narrow.png`：390x844，从失败运行返回同一模型修复，页面无横向溢出。
- 结果：1/1 PASS；pageerror、console error、requestfailed、HTTP >= 400 均为 0。

浏览器回归发现并修复了一个真实 production bundle 问题：HashRouter 查询参数位于 `window.location.hash`，原实现只读取 `window.location.search`，导致实现上下文丢失。修复后重新构建一次并定点复验通过。

## 事实边界

后端事实由 Spring + PostgreSQL 17.4 Testcontainers 集成测试证明；浏览器场景使用精确 API mock，只证明 production bundle、路由上下文、失败回链、窄屏与 Chromium 95 兼容。真实部署、认证以及外部 dbt/Airflow/Catalog 联动保留给 F6-T03。
