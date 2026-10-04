# Sprint-13 集成验证（IT）

**目的**：保证 Sprint-13 交付物端到端正确，而不只是各自单元测试过。

## 当前已完成验证

- [x] `source/dts-analytics` 编译通过
- [x] `source/dts-platform` 编译通过
- [x] `source/dts-platform-webapp` 生产构建通过（`LEGACY_BROWSER_BUILD=1 pnpm build`）
- [x] 新路由已注册：`/bi/explore`、`/bi/card/new`、`/bi/card/:id/edit`、`/bi/virtual-datasets`
- [x] 旧入口兼容：`/bi/questions/:id/edit` 会按卡片类型自动分流到旧 editor 或新语义 editor
- [ ] 现场 Chrome 95 录屏、慢查询熔断、使用度统计与 Git PR adapter 仍待补证

## 任务引用约定

跨 Feature 引用统一使用 `F#/T#`。
例如：`F1/T01` 表示“接口合约与 DSL 规范”的第一项任务；裸 `T01` 只在单个 Feature 文档内部使用。

## 验证矩阵

### 一、Spec 冻结证据（F1）

存 `evidence/f1-specs/`：

- [ ] `01-schema-yml-meta-dts.md` — 冻结
- [ ] `02-semantic-rest-api.md` + OpenAPI yaml
- [ ] `03-derived-metric-dsl.md` + ANTLR g4
- [ ] `04-virtual-dataset-and-arrow.md` + JSON schema
- [ ] 跨职能评审纪要 `f1-review-minutes.md`

### 二、端到端场景（核心）

存 `evidence/e2e/`：

#### 场景 A：单表指标查询
1. 工程师按 `F1/T01` 规范写 `ads_sales_daily.schema.yml`（3 个 metric + 2 个 dimension）
2. `dbt run` + `dbt compile` 产出 manifest.json
3. 平台 ingest manifest
4. 分析师在 `/bi/card/new` 选择 base + measure + dimension + time granularity
5. 查询成功，SQL 预览显示正确
6. 保存 Card

**证据**：录屏 + SQL 对比表 + Card 截图

#### 场景 B：两表 join + fanout
1. dbt 声明 `orders × customer`（M:1）和 `orders × payments`（1:N）
2. 分析师在画布拖 orders + customer + payments
3. 画布显示 payments 边为红色虚线 + fanout 警告
4. 请求 count(orders.order_id) + sum(payments.amount) GROUP BY customer.region
5. 查询返回结果
6. SQL 预览显示 SymmetricAggregateRewriter 产出的 CTE 改写
7. 结果数值与人工参考 SQL 完全一致

**证据**：录屏 + 两份 SQL + 两份数据集对比

#### 场景 C：派生指标
1. 分析师在 Card Editor 新建派生指标 `[付费率] = [付费订单数] / [总订单数]`
2. 编辑器实时校验通过
3. SQL 预览显示正确展开
4. 查询跑通，结果正确
5. 保存 Card

**证据**：录屏 + SQL

### 三、安全场景（F6/T02）

存 `evidence/security/`：

#### 密级矩阵
| 用户密级 | 目标 | 期望 | 证据 |
|---|---|---|---|
| INTERNAL | INTERNAL model | 200 | curl 响应 |
| INTERNAL | CONFIDENTIAL model | 422 | curl 响应 + 错误码 |
| INTERNAL | 含 SENSITIVE 列 | 列 = "***" | 查询结果截图 |
| OP_ADMIN | CONFIDENTIAL | 200 + 审计 | 审计日志 |

#### 白名单拦截
- 非白名单 join → 422 `JOIN_NOT_WHITELISTED`
- UI 拖拽 attempt → 前端拦截 + toast
- **双重保险验证**：前端绕过 devtool 直接 call API，后端仍 422

### 四、治理验证（F6/T03, F6/T04）

存 `evidence/governance/`：

- [ ] 慢查询 30 秒熔断：人造 `SELECT pg_sleep(40)` → 504
- [ ] 并发限流：并发 4 个查询，1 个 429
- [ ] VDS 使用度面板截图（至少 5 条 VDS 统计）
- [ ] VDS Promote：从点击到 Git PR 生成的完整链路录屏

### 五、前端回归

存 `evidence/ux/`：

- [ ] 新菜单"BI 分析"3 个子项可访问
- [ ] 老 `IndicatorsPage` "新建指标"按钮改为跳转
- [ ] CardEditor 三栏布局 OK（指标树 / 画布 / 属性面板）
- [ ] 派生指标编辑器补全 + 错误提示
- [ ] 暗色/亮色主题无破

### 六、性能基线

存 `evidence/perf/`：

- [ ] 单表查询 p95 < 500ms（缓存命中 < 50ms）
- [ ] 两表 join 查询 p95 < 2s
- [ ] 画布 10 节点渲染 < 100ms
- [ ] 指标树 500 节点虚拟滚动 < 16ms/帧
- [ ] manifest ingest 1000 metric 规模 < 5s

### 七、审计日志

存 `evidence/audit/`：

- [ ] 5 类审计事件（查询执行、VDS 写入、promote PR、密级越限、OP_ADMIN 旁路）样本
- [ ] 审计表索引建立，支持按 user / day / event_type 查

### 八、老 Metabase fork 共存

存 `evidence/legacy-coexistence/`：

- [ ] 老 Card（`engine=mbql_legacy`）仍可查看和执行
- [ ] 老 Dashboard 仍可访问
- [ ] 无路由冲突
- [ ] 两套 engine 的 Card 在同一 Dashboard 里可共存

### 九、Chrome 95 兼容（客户环境硬约束）

存 `evidence/chrome-95/`：

#### 依赖版本锁（预设）
- [ ] `reactflow@^10.x`（避开 v11 的 container queries）
- [ ] `apache-arrow@^12` 或 `^13`（避开 top-level-await 的 esnext-esm 版本）
- [ ] 全代码库禁用 `:has()` / `@container` / `structuredClone`（grep 检查通过）
- [ ] vite 构建 `LEGACY_BROWSER_BUILD=1`（target chrome95）通过无 error

#### 实机 smoke 场景（客户 Chrome 95 浏览器）
- [ ] TC-CR-01：打开 `/bi/explore`，指标树和维度树加载成功
- [ ] TC-CR-02：进 `/bi/card/new`，拖入 1 个 base + 1 metric + 1 dimension 出 SQL 预览
- [ ] TC-CR-03：模型画布拖 3 个模型并连接 join，fanout 警告正常显示
- [ ] TC-CR-04：派生指标编辑器输入公式 `revenue / cost`，SQL 预览正确
- [ ] TC-CR-05：Card 执行，结果表格 + 图表都能渲染，无 console error

#### 证据
- 每个 TC 截图或录屏存 `evidence/chrome-95/TC-CR-0X-*.png`
- 有任何 TC 不通过，fallback 方案写入 `issues/chrome-95-<slug>.md`

## 缺陷跟踪

运行 IT 期间发现的 bug 写入 `issues/`，每个以日期命名：`20260423-<slug>.md`，含复现步骤 + 影响范围 + fix PR 链接。

## 不在本 IT 范围

- 老 Card 迁移工具（Sprint-14+）
- 多源联邦 / Arrow 默认开启（未来）
- 窗口函数派生指标（保留，Phase 2）
- Git 生产环境 webhook（只验证本地 adapter）
