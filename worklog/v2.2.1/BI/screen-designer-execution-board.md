# 大屏重构执行看板（统一内核 + 可复用落地）

> 日期：2026-02-13  
> 目标：把“架构方案 + 功能清单”转换为可直接排期执行的交付看板。  
> 原则：借鉴 Metabase / Superset / DataEase 的设计思想，不复制其源代码。

---

## 1. 交付边界
- 交付对象：`source/dts-analytics-webapp/modern` + `source/dts-analytics`。
- 交付方式：先统一协议与运行时，再迁移现有功能，最后做增强能力。
- 当前阶段：研发期，可不兼容历史实验数据；但需保留可验证的演示链路。

## 2. 模块映射（做什么放在哪）

| 能力域 | 前端模块（modern） | 后端模块（analytics） | 验收关键点 |
|---|---|---|---|
| 协议内核 | `src/features/bi-kernel` | `/api/schema`（可选） | `QuerySpec/VizSpec/ScreenSpec` 固定化 |
| 设计器 | `src/features/bi-designer` | `Screen/Version API` | 设计器仅读写 `ScreenSpec` |
| 运行时 | `src/features/bi-runtime` | `Query Runtime API` | 联动、缓存、降级可控 |
| 适配器 | `src/features/bi-adapters/*` | 外部查询代理/鉴权 | Metabase/Superset 输入转统一协议 |
| 插件系统 | `src/features/bi-plugins/*` | 插件注册清单（可选） | 新图表不改核心代码即可接入 |
| 治理发布 | 发布页/版本页/ACL 页 | 版本、ACL、审计、分享 API | 草稿发布隔离 + 安全分享 |

## 3. 周计划（建议先执行 4 周）

### Week 1（内核冻结）
- W1-1：完成 `Spec v2` 定义与 schema 校验（F-001~F-005）。
- W1-2：抽离 `bi-kernel` 与 `bi-runtime` 最小骨架（F-101, F-201）。
- W1-3：完成 `metabase-adapter` 最小链路（F-301）。
- W1-4：补第一批契约单测（F-701, F-702）。
- 里程碑：M0 可达成，允许进入设计器迁移。

### Week 2（设计器迁移）
- W2-1：画布与属性面板切到 `ScreenSpec`（F-101, F-102, F-103）。
- W2-2：表格组件升级“列标题自定义 + 字段自由绑定”（Table P0）。
- W2-3：统一错误层（错误码 + requestId + 可重试）（F-204, F-205）。
- W2-4：核心路径 E2E：新建 -> 配置 -> 预览 -> 发布（F-703）。
- 里程碑：现有模板在新内核下可编辑、可预览。

### Week 3（治理与发布）
- W3-1：草稿/发布/回滚模型（F-501, F-502）。
- W3-2：ACL 与分享安全（F-503, F-504）。
- W3-3：审计日志落库与查询（F-505）。
- W3-4：兼容性回归（Chrome 95/109/最新版）（F-704）。
- 里程碑：达到商用试点准入（P0 能力闭环）。

### Week 4（增强能力）
- W4-1：全局变量与联动规则（F-602, F-603, F-604）。
- W4-2：数据源统一入口（metric/dataset/sql/card/api）（F-601）。
- W4-3：ECharts 插件包 + 表格插件完善（F-403, F-404）。
- W4-4：性能压测与缓存策略（F-202, F-705）。
- 里程碑：形成“可复制交付”的 P1 基线。

## 4. 关键依赖与阻塞判定
- D-001：平台统一身份/组织/权限接口稳定可用（阻塞 ACL 落地）。
- D-002：外部数据源网络连通与证书策略明确（阻塞 adapter 现场验证）。
- D-003：日志与指标采集可接入（阻塞 P0 可观测闭环）。
- D-004：现网域名与反向代理规则固定（阻塞公开分享/跨网段访问验证）。

## 5. 任务拆分规则（执行时必须遵守）
- 每个任务必须同时包含：代码改动 + 测试用例 + worklog 证据。
- 每个任务必须给出：影响范围、回滚方案、验收命令。
- 每个任务合并前必须经过：协议兼容检查（至少 `Spec v2` schema 校验）。
- 每周至少一次回归：发布链路、分享链路、表格绑定链路。

## 6. 立即可开的第一批任务（Next 5）
1. `SD-KERNEL-001`：定义并冻结 `QuerySpec/VizSpec/ScreenSpec` Type + schema。
2. `SD-DESIGNER-001`：设计器数据流改造为单一 `ScreenSpec` source of truth。
3. `SD-TABLE-001`：Table 组件支持“自定义表头 + 列绑定映射 + 格式化”。
4. `SD-RUNTIME-001`：统一错误模型（code/message/requestId/retryable）。
5. `SD-ADAPTER-001`：Metabase card 查询适配器首版打通并补契约测试。

## 7. 完成定义（Definition of Done）
- 协议层：有 schema + 单测 + 示例配置。
- 交互层：有 UI 操作路径 + E2E 覆盖。
- 服务层：有可观测字段（错误码、requestId、耗时）落盘。
- 发布层：可发布、可回滚、可审计。
- 文档层：`worklog/v2.2.1` 任务状态与证据同步更新。
