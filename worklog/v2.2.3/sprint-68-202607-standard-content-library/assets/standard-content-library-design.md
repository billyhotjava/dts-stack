# Sprint-68 标准内容库设计

## 1. 能力定义

面向 DTS 实施人员和客户数据治理管理员，提供一套随产品离线交付、可选择安装、可本地修改、可持续升级的标准内容库。安装后，客户以 DTS 通用基线为起点，只补充组织责任、内部码表和行业差异，而不是从空库逐条录入。

## 2. 所有权边界

| 对象 | 正文所有者 | 内容包职责 | 下游引用 |
|---|---|---|---|
| 业务术语 | 标准模块 | 提供参考定义、来源和版本 | 指标、模型、资产只保存稳定引用 |
| 数据元 | 标准模块 | 提供通用字段约束、码表/单位引用 | ModelSpec 字段绑定稳定 ID/版本 |
| 公共码表 | 标准模块 | 提供目录、码值、父子关系和来源映射 | 数据元、质量、接入消费 |
| 计量单位 | 标准模块 | 提供单位、量纲、换算和基准单位 | 数据元、指标和模型字段消费 |
| dbt 模型 | 建模模块/PJM 包 | 仅作为候选语义和血缘证据 | 不直接写入标准正文 |
| 指标与公式 | 指标模块 | 内容包可声明依赖但不复制正文 | 标准模块不接管指标事实源 |

## 3. 内容包制品

每个包是一个签名 ZIP，最少包含：

```text
manifest.json
01-business-terms.csv
02-data-elements.csv
03-reference-code-directories.csv
04-reference-code-items.csv
05-reference-code-mappings.csv
06-measurement-units.csv
LICENSE-NOTICE.md
SOURCE-REGISTER.json
checksums.sha256
```

允许缺少不适用的 CSV，但 `manifest.json`、来源登记和校验和不可缺少。

### 3.1 manifest v2

```json
{
  "schemaVersion": 2,
  "packageCode": "dts-core-person",
  "packageVersion": "1.0.0",
  "name": "自然人基础",
  "category": "DTS_BASELINE",
  "industry": ["COMMON"],
  "standardEditions": ["GB/T 2261.1-2003"],
  "releasedAt": "2026-07-23",
  "effectiveFrom": "2026-07-23",
  "sourceRegister": "SOURCE-REGISTER.json",
  "licenseDecision": "APPROVED",
  "dependencies": [],
  "replaces": [],
  "checksum": "sha256:..."
}
```

必须校验包编码、语义版本、依赖闭环、版本单调性、许可证结论、来源登记和签名。

## 4. 记录级溯源

术语、数据元、码表目录/码值和计量单位至少具备：

- `origin_package_code`
- `origin_package_version`
- `origin_record_key`
- `origin_source_ref`
- `origin_standard_no`
- `origin_standard_edition`
- `origin_checksum`
- `baseline_revision`
- `local_override`
- `deprecated_at`
- `replaced_by`

`source_system` 继续表达业务来源系统，不能再被复用为标准内容来源。

## 5. 三层生效模型

```text
系统基线 Baseline
  + 客户扩展 Customer Extension
  + 本地改写 Local Override
  = 当前生效视图 Effective View
```

- Baseline：由签名内容包管理，保存上游版本和校验和。
- Customer Extension：客户新增的独立记录，不受包卸载或升级影响。
- Local Override：只保存相对基线的差异；保留所覆盖的 baseline revision。
- Effective View：API 和页面默认展示的最终值，同时可以展开来源和差异。

## 6. 安全升级

升级使用三方比较：

```text
旧基线 B0
新基线 B1
客户生效值 L
```

| 情况 | 行为 |
|---|---|
| B0 = L，B1 变化 | 自动采用 B1 |
| B0 ≠ L，B1 = B0 | 保留本地改写 |
| B0 ≠ L，B1 ≠ B0，且改动字段不重叠 | 自动合并并记录 |
| B0 ≠ L，B1 ≠ B0，且同字段冲突 | 阻断升级，要求人工选择 |
| B1 弃用记录 | 保留当前引用，提示替代项，不直接删除 |

任何自动合并都必须产生审计记录，并允许按安装 run 回滚。

## 7. PJM 候选数据流

```text
dbt source/yml/sql/handbook
  → 只读资产清单
  → 排除生成物、测试行和页面制品
  → 提取候选术语/字段/Canonical dim/Alias mapping
  → 六道准入评分
  → 人工审核
  → 通过项进入 pjm-project-management-reference
  → 与 DTS 通用基线做稳定键和语义去重
```

PJM 不允许直接写 `standard-packages/`。所有候选必须先生成带理由的准入报告。

## 8. 六道准入门禁

1. **通用性**：是否跨两个以上业务过程或客户场景成立。
2. **血缘**：能否追溯到 source → STG → DWD；不能只来自看板文案。
3. **重复度**：是否已被 DTS 基线稳定键、同义词或码表覆盖。
4. **命名**：中文名、英文名、数据类型和定义是否去掉报表/客户/技术层噪声。
5. **质量**：唯一、非空、accepted-values、引用完整性必须以阻断级测试通过。
6. **客户适用性**：是否会把 PJM 专属状态或有损映射强加给所有客户。

准入结果只有 `ACCEPT`、`CONDITIONAL`、`REJECT`；`CONDITIONAL` 不允许构建进 DTS 通用默认画像。

## 9. 失败与恢复

- 包签名、校验和、依赖、许可证或跨文件引用失败：preview 阻断，不产生业务记录。
- 本地覆盖冲突：保持旧版本生效，不允许部分升级。
- apply 事务失败：整包回滚。
- 内容包升级后发现运行问题：按 run 回滚基线，但不删除客户扩展。
- 远程内容目录不可达：继续使用随发布包交付的离线目录。

## 10. 安全与审计

- 只有标准维护角色可安装、升级、解决冲突和卸载内容包。
- 普通查看者可以查看来源、版本和许可证结论，但不能获取未授权标准全文。
- 所有安装、升级、覆盖、冲突选择、弃用和回滚记录操作者、时间、包版本和 diff 摘要。
- 多租户内容安装和本地覆盖严格按租户隔离；请求头不能扩大权限或切换到无权租户。

## 11. 测试边界

- Contract：manifest v2、六 CSV、依赖、稳定键、三方合并。
- Content CI：来源登记、许可证、重复、空值、引用、校验和、敏感/客户词扫描。
- Backend：preview/apply/upgrade/conflict/rollback/tenant/audit。
- PostgreSQL：真实版本升级和本地覆盖保持。
- Frontend：内容目录、画像安装、冲突处理、失败恢复。
- Browser：Chrome 95 桌面与窄屏。

## 12. 交付决策

Sprint-68 只有在“内容来源合法、客户修改安全、离线可安装、真实数据库可回滚、浏览器可操作”同时成立时才能 DONE。条目数量不是单独的完成证据。
