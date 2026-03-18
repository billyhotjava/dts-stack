# v2.2.2 Sprint-1 计划

## Sprint 目标

解决 v2.2.1 现场交付中暴露的阻塞问题 + 现场新需求落地。

---

## P0 — 必须完成（现场阻塞）

### BUG-001: Chrome 95 菜单选中色不可读
**现象**: admin/platform/analytics 三个 webapp 左侧菜单选中时出现蓝暗色，文字看不清。
**根因**: 菜单 active 状态背景色与文字色对比度不足，可能涉及 OKLCH 色彩空间的 fallback 问题（Chrome 95 不支持 OKLCH，需确认 sRGB fallback 是否正确）。
**范围**: 三个 webapp 的侧边栏 CSS。

### BUG-002: ELT 任务 DAG 未就绪时应禁止运行
**现象**: DAG 上传/同步偏慢，用户在 DAG 未注册完成时点运行，报 502。
**方案**: 前端在 DAG 同步中时禁用运行按钮，显示"DAG 正在同步中，请稍后再试"。后端已有 `/api/etl/dbt/sync/status` 接口。

### BUG-003: Excel 入湖修改字段后旧表未 DROP
**现象**: 编辑 Excel 入湖任务修改了字段，但 ODS 表结构未更新（旧列还在），Addax 报字段错误。
**根因**: 重新上传时只做了 DELETE 行数据，没有 DROP + 重建目标表。
**方案**: 检测字段变化时先 DROP TABLE 再重建。

### BUG-004: Analytics 数据源无法删除
**现象**: dts-analytics-webapp 升级后不能删除数据源。
**排查方向**: `DatabaseResource.delete()` 接口、权限校验、连接池缓存清理。

### BUG-005: Analytics 与 Platform session 不同步
**现象**: 从 platform-webapp 打开 analytics-webapp，platform 侧 10 分钟无操作后，analytics 提示 session 失效。
**根因**: 两个 webapp 使用独立的 session 模型（platform 用 JWT + localStorage，analytics 用 Metabase session），没有共享心跳。
**方案**: analytics 侧增加对 platform token 的校验/续期，或统一走 SSO token。

### SEC-001: 附件密级管控
**需求**: 非密模块禁止上传含"机密"字样的附件；机密模块需判断人员密级 ≥ 附件密级才允许上传。
**范围**: 所有上传附件场景（platform-webapp、admin-webapp）。

### PM-001: 项目管理数仓模型适配客户真实数据
**需求**: 客户 Excel 和 demo 测试数据差异大，需重新设计清洗逻辑。
**子项**:
- PM-001a: 项目编号 = 项目名称，分系统/分任务 = 子项目（父子关系），重新设计维度推导
- PM-001b: 处理脏数据（`/` 分隔符在内容中、`A4-A3` 计算字段、`#value!` 错误值），dbt 层清洗
- PM-001c: 650 条 Excel 只入库 350 条，排查 Addax 搬运丢数据原因
- PM-001d: 重新生成模型 zip 包

---

## P0 — 必须完成（v2.2.1 遗留）

### BE-001: dbt 模型上线链路修复
**问题**: UI 导入 zip → 点"上线"不生成数仓表，但手动 dbt 可以。
**交付标准**: 新环境导入 zip → 点上线 → 21 张表全部生成。

### FE-001: 项目看板树状折叠验证
**状态**: 代码已完成，待远程构建验证。

---

## P1 — 应该完成

### BE-002: 项目管理指挥中心大屏 API
**来源**: v2.2.1 sprint-13 Phase 1。
**范围**: `/api/project-cockpit/screen/*` 聚合接口。

### FE-002: 大屏全屏缩放适配（支持 2K 放大）

### FE-003: API 数据源全局变量透传

### OPS-001: dbt 模型包自包含验证（本地全流程验证）

### OPS-002: 构建脚本鲲鹏远程验证（`--bg` 模式）

---

## P2 — 有余力时完成

### FE-004: 前端首屏加载优化
### BE-003: 项目看板数据质量面板增强
### DOC-001: 现场部署手册

---

## 技术决策记录

| 决策 | 原因 | 日期 |
|---|---|---|
| dbt 维度模型从 ODS 自动推导，去掉 seed 依赖 | seed 模板与实际数据不匹配 | 2026-03-17 |
| schema.yml 所有测试降为 `severity: warn` | 客户数据脏，测试阻断数据流 | 2026-03-17 |
| 质量门禁 `blocking` 基于 `blockers` 列表 | 前端被空 blockers 阻断 | 2026-03-17 |
| enriched 模型 COALESCE 兜底推导 | 兼容有/无 seed 环境 | 2026-03-17 |
| 构建脚本移除 `--memory-swap`，增加 `--bg` | SSH 断连问题 | 2026-03-17 |
| UI zip 增加 macros/parse_date_safe.sql | 新环境缺宏导致编译失败 | 2026-03-18 |
| Addax 只搬运(text)，dbt 负责清洗转换 | 客户 Excel 含公式/特殊字符 | 2026-03-18 |
