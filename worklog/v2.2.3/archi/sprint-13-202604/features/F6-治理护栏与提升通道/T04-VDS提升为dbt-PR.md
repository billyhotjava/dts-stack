# T04: VirtualDataset → dbt PR 生成器

**优先级**: P1
**状态**: READY
**依赖**: F3/T04

## 目标

分析师在画布上构建的虚拟数据集（VDS）如果被长期、频繁使用（T03 使用度统计判断），可以 **"提升"** 为正式 dbt model：
1. 系统根据 VDS 定义生成 **dbt SQL** + **`schema.yml` 片段**
2. 通过 **git API**（GitLab / Gitea / 本地 bare repo）提交一个 PR
3. 等工程师 review / approve / merge
4. 合并后的新 dbt model 在下次 manifest ingest 时进入指标树
5. VDS 标记为 `promoted`，后续查询可被重定向到新 model（保持 Card 引用不变）

**核心价值**：让沙盒自由度不失控——有进有出的闭环治理。

## 技术设计

### 1. 触发方式

- VDS 详情页 "提升到 dbt" 按钮（仅 `BI_DATA_ENGINEER` / `OP_ADMIN` 可见）
- 按钮的展示条件：T03 "建议提升"规则（30+ 天、50+ 查询、2+ Card 引用），未达标也可强制触发

### 2. dbt 代码生成

**输入**：VirtualDatasetDefinition（F1/T04 JSON）
**输出**：`models/promoted/<vds_id>.sql` + `schema.yml` 片段

#### SQL 生成

走现有 `QueryCompiler` 的 `compileToSql()` 方法（复用 F2/F3 编译能力），但做以下调整：
- 不加 SecurityInjector（dbt 编译成 view，安全由 Ranger / DB 行级再做一次）
- 不加 filter（VDS 的 default_filters 写进 `config.where_clause` 或者不翻译，工程师手工确认）
- 用 `{{ ref('ads_sales_daily') }}` 替代物理表名

示例输出：
```sql
-- models/promoted/vds_a1b2c3.sql
{{ config(
    materialized='table',
    tags=['promoted', 'vds'],
    meta={'vds_id': 'vds_a1b2c3', 'promoted_from_vds': true}
) }}

SELECT
    base.customer_id,
    date_trunc('day', base.order_date) AS order_date_day,
    t1.region,
    SUM(base.revenue_cents) AS revenue,
    COUNT(DISTINCT base.order_id) AS order_count
FROM {{ ref('ads_sales_daily') }} base
LEFT JOIN {{ ref('dim_customer') }} t1
    ON base.customer_id = t1.customer_id
GROUP BY 1, 2, 3
```

#### schema.yml 生成

```yaml
version: 2
models:
  - name: vds_a1b2c3
    description: "Promoted from VirtualDataset: <name> (original id: vds_a1b2c3)"
    meta:
      dts:
        spec_version: "1"
        exposed_to_modeler: true
        security_level: INTERNAL
        subject_area: sales
        grain: "order × day × region"
    columns:
      - name: revenue
        description: 营收（累计）
        meta:
          dts:
            metric:
              type: sum
              label: 营收
              format: { type: currency_cny, scale: 100 }
      - name: order_count
        # ...
      - name: region
        meta:
          dts:
            dimension:
              type: categorical
              label: 地区
      - name: order_date_day
        meta:
          dts:
            dimension:
              type: time
              label: 日期
              granularities: [day, week, month]
```

派生指标保留为 derived metric（不物化到 dbt），因为本质是 SQL 函数。

### 3. Git 提交

抽象一层 `GitAdapter` 接口，支持多种后端：

```java
public interface GitAdapter {
    PullRequest createBranch(String baseBranch, String newBranch);
    void writeFile(String branch, String path, String content);
    PullRequest openPR(String branch, String title, String body);
}

@Service @Primary
public class GiteaAdapter implements GitAdapter { ... }

@Service
public class LocalBareRepoAdapter implements GitAdapter { ... }  // 开发环境
```

Spring profile 选择 adapter：`spring.profiles.active=gitea` / `local`。

### 4. PR 内容

Title: `[VDS Promotion] vds_a1b2c3 → dbt model`
Body（自动生成）：

```markdown
## VDS 提升请求

**VDS ID**: vds_a1b2c3
**名称**: 华东区销售 × 客户分层
**Owner**: u1001
**创建时间**: 2026-04-01
**使用情况**: 近 30 天查询 124 次，被 3 个 Card 引用

## 生成的文件

- `models/promoted/vds_a1b2c3.sql`
- `models/promoted/schema.yml` (appended)

## Reviewer 待确认

- [ ] SQL 正确性（编译器自动生成，建议人工 review join 顺序）
- [ ] `schema.yml` 的 `description` / `label` 是否需要润色
- [ ] 是否需要 `materialized: incremental` 或 `materialized: view` 替代 `table`
- [ ] 添加 `tests:` 数据质量断言
- [ ] 主题域 `subject_area` 是否正确

## Post-merge 动作

合并后：
1. `dbt run --select vds_a1b2c3`
2. 平台自动重跑 `manifest ingest`
3. VDS `vds_a1b2c3` 状态更新为 `promoted`
4. 通知 owner @u1001
```

### 5. VDS 状态联动

- 按钮点击：VDS state `shared` → `promoted_pending`，写 `promoted_pr_url`
- PR merge 监听：webhook 或轮询 → 下次 manifest ingest 识别到新 model → VDS state `promoted_pending` → `promoted`
- VDS `promoted` 后**只读**，用户不能再改画布

### 6. Card 重绑

Card 引用 VDS 时 query 里有 `virtual_dataset_id`。VDS promoted 后：
- 新查询**仍走 VDS** 到新 dbt model（透明）
- 可选：管理端手动触发"Card 迁移"把 Card.query 改为直接基于新 dbt model（减少间接层）

本 Sprint 只做透明跳转，迁移工具放 Sprint-14。

### 7. 失败处理

- Git API 失败：VDS 回滚 state，保留 error log
- 生成 SQL 失败（编译器异常）：前端显示错误码 + 修复建议
- PR 被拒绝：工程师 reject 后自动把 VDS state 退回 `shared`，owner 收到通知

### 8. 安全

- 仅工程师 + OP_ADMIN 可 promote（F6/T01 已 enforce）
- Git 凭据放 `application.yml` + secret manager，不硬编码
- 生成的 SQL 经白名单 function 校验（不会出现任意函数）

### 9. 前端

VDS 详情页顶部：
```
[提升到 dbt]  ←仅工程师可见
状态：shared

——

提升到 dbt
┌──────────────────────────────────────┐
│ 将为该虚拟数据集创建 dbt PR          │
│ 请确认：                              │
│ ☑ 生成的 SQL 通过语义层编译           │
│ ☑ 已运行 30 + 天 / 124 次查询         │
│ ☑ 被 3 个 Card 引用                   │
│                                      │
│ [预览 SQL] [预览 schema.yml] [取消]  │
│                       [生成 PR]      │
└──────────────────────────────────────┘
```

### 10. 测试

- 端到端：VDS → promote → 观察 Git repo 生成文件 + PR URL
- Git adapter mock：local bare repo 验证流程
- 失败路径：Git 不可达、SQL 生成异常

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/promote/VdsPromoter.java` |
| 新建 | `service/semantic/promote/DbtFileGenerator.java` |
| 新建 | `service/semantic/promote/GitAdapter.java` + 实现 |
| 新建 | `web/rest/semantic/VdsPromoteResource.java` |
| 修改 | `GovVirtualDataset` 支持 state transitions |
| 新建 | `src/pages/bi/virtual-datasets/components/PromotePanel.tsx` |
| 测试 | promoter + Git adapter + 端到端 |

## 验证

- [ ] 端到端 demo：从 VDS 生成 PR，观察 git repo 里的文件（录屏存 `it/evidence/f6-promote-demo.mp4`）
- [ ] PR body 正确包含统计数据
- [ ] VDS 状态机正确转换
- [ ] merge 后 VDS 状态 `promoted`
- [ ] 生成的 dbt model 能通过 `dbt compile`
- [ ] 权限拒绝场景 403

## 完成标准

- [ ] 端到端 demo 成功
- [ ] 单元 + 集成测试覆盖率 ≥ 70%
- [ ] 运维文档描述 Git adapter 配置 `assets/specs/09-promote-pipeline.md`
