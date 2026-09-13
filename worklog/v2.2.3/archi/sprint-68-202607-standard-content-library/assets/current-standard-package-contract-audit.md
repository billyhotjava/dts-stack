# Sprint-68 F1-T01：现有标准包契约与升级风险审计

**审计日期**：2026-07-23  
**审计范围**：Sprint-57 标准包模板、上传预检、应用、内置包安装、导入历史、回滚、前端入口和持久化模型  
**结论**：现有管道可复用为“解析和整包事务执行骨架”，但不能直接承担内容升级。当前重新安装会按业务键覆盖客户记录，缺少包版本、记录来源、本地覆盖、依赖、完整性和并发保护。

## 1. 当前执行链

```text
StandardPackagePage
  ├─ 下载模板
  │    GET /api/modeling/metadata-standards/template
  │      -> ModelingResource.buildMetadataTemplateZip
  ├─ 上传预检
  │    POST /api/modeling/standard-packages/import/preview
  │      -> StandardPackageImportService.previewZip
  │      -> 解析 01~05 CSV、跨文件校验
  │      -> std_pkg_import_run(PREVIEWED, preview_json, payload_json)
  ├─ 确认应用
  │    POST /api/modeling/standard-packages/import/apply
  │      -> StandardPackageApplyService.apply
  │      -> 术语 → 码表目录 → 码值 → 数据元 → 映射
  │      -> std_pkg_import_run_item(before-image)
  │      -> std_pkg_import_run(APPLIED)
  ├─ 内置包安装
  │    POST /api/modeling/standard-packages/builtin/{code}/install
  │      -> classpath manifest.json + CSV
  │      -> 同一 preview → apply 管道
  └─ 整包回滚
       POST /api/modeling/standard-packages/runs/{runId}/rollback
         -> 按 run item 倒序删除 CREATE、恢复 UPDATE
         -> std_pkg_import_run(ROLLED_BACK)
```

接口写操作由 `CATALOG_MAINTAINERS` 权限保护；内置包列表和导入历史查询可直接读取。

## 2. 五类文件契约

| 文件 | 当前稳定匹配键 | 新增/更新判定 | Apply 覆盖字段 | 主要风险 |
|---|---|---|---|---|
| `01-business-terms.csv` | `lower(term_code)` | `findByCodeLowerIn` | code、name、aliases、domain、definition、owner、ownerDept、tags、versionNotes、version | 数据库无 term code 唯一约束；包内 `status` 被忽略并强制写为 `ACTIVE` |
| `02-data-elements.csv` | `lower(field_name_en) + lower(domain)` | `findByFieldNameEnIgnoreCaseAndDomainIgnoreCase` | 中文/英文名、类型、长度、精度、标度、可空、域、说明、sourceSystem、codeSet、默认值、主键、安全等级 | `source_system` 同时被当成业务来源；重新安装覆盖客户编辑并触发数据元版本变化 |
| `03-reference-code-directories.csv` | `lower(code_type_code)` | `findByCodeTypeCodeIgnoreCase` | 名称、标准层级、业务目录、类型、状态、责任部门、版本 | `code_type_id` 创建后不再更新；无包来源和本地覆盖判断 |
| `04-reference-code-items.csv` | `code_type_id + code_value` | 先加载目录全部码值，再按原始字符串匹配 | 名称、说明、排序、父码、默认标志 | 大小写和空白语义不统一；客户修改会被重装覆盖 |
| `05-reference-code-mappings.csv` | `code_type_id + source_system + source_code` | 加载目录全部映射后匹配 | `standard_code` | preview 永远报告 `toCreate`，不能识别将被更新的映射；来源系统映射与内容来源概念容易混淆 |

当前包允许只提供部分 CSV；完全没有 01～05 文件才阻断。计量单位不在契约中。

## 3. 现有可复用能力

| 能力 | 现状证据 | Sprint-68 决策 |
|---|---|---|
| ZIP 安全边界 | 最多 64 个条目、单文件 10 MiB、总解压 40 MiB，拒绝包含 `..` 的路径 | 保留，并为 manifest/source register 增加类型和大小限制 |
| CSV 解析 | UTF-8、BOM 处理、统一行号错误 | 保留；第六个计量单位 CSV 复用同一解析方式 |
| 跨文件引用 | 目录、码值、父码、数据元 code set 可在本包或数据库解析 | 保留；依赖包解析必须在此之前完成 |
| 两阶段执行 | preview 不写业务表，apply 仅消费已落库 payload | 保留；preview 需增加包版本、三方 diff 和冲突结果 |
| 整包事务 | apply 和 rollback 均由事务服务执行 | 保留；增加并发控制和基线 revision 校验 |
| Before-image | UPDATE 保存修改前字段，CREATE 保存实体 ID | 保留为操作回滚证据，但不能替代基线版本模型 |
| 内置/上传同管道 | classpath 包复用 preview/apply | 保留；内容来源改为外部版本化仓库和离线制品 |
| 权限和审计 | 写操作要求目录维护者，preview/apply/rollback 都写审计事件 | 保留；查询历史、包目录和下载也需补充租户/可见性边界 |

## 4. 升级风险矩阵

| ID | 风险 | 级别 | 当前行为 | Sprint-68 处理 |
|---|---|---|---|---|
| R01 | 客户本地修改被重装覆盖 | **P0** | 只要业务键相同即记录 before-image 后覆盖，没有 checksum/revision 比较 | F2 建立基线、客户扩展、本地改写三层和三方 diff；冲突 fail closed |
| R02 | 术语被强制发布 | **P0** | `copyTermFields` 无条件写 `status=ACTIVE`，忽略 CSV status | F1/T02 保留 v1 兼容告警；v2 默认 `REFERENCE/DRAFT`，不得静默 ACTIVE |
| R03 | 缺少包身份和版本 | **P0** | run 只有上传文件名或包 code，没有 schemaVersion/packageVersion | manifest v2 提供稳定包身份、版本和依赖 |
| R04 | 缺少记录级来源 | **P0** | 业务表不知道记录来自哪个包；run item 只属于一次执行 | F1/T03 增加稳定 origin record key、包版本、来源和基线 checksum |
| R05 | `source_system` 语义混用 | **P0** | 数据元仅有业务来源系统字段，无法表达标准来源 | 保留 `source_system` 业务语义，单独建立 provenance |
| R06 | rollback 可覆盖回滚后的新编辑 | **P0** | 回滚 UPDATE 时直接恢复 before-image，不校验当前实体是否又被用户修改 | 回滚前比较 revision/checksum；发生漂移时阻断并列冲突 |
| R07 | rollback CREATE 可能删除已被引用或继续编辑的数据 | **P0** | 直接 repository delete；未执行引用保护和当前版本检查 | 新增引用检查、revision 检查和显式处置 |
| R08 | 并发 apply/rollback | **P0** | run 无 `@Version`/CAS；业务实体没有统一升级锁 | run 乐观锁 + 包/租户级互斥 + 实体 revision 校验 |
| R09 | 包依赖靠人工顺序 | **P1** | common-data-elements 仅在描述中建议先安装三张码表 | manifest v2 dependencies + 拓扑排序 + 缺失/循环/版本错误 |
| R10 | 完整性和许可不可验证 | **P0** | classpath manifest 没有 checksum、签名、来源或许可证 | F1/T02/T04 增加文件 SHA-256、来源登记和许可门禁 |
| R11 | “已安装”判断不代表当前版本 | **P1** | 只判断是否存在任意同 code、BUILTIN、APPLIED run | 改为包 code + version + content digest 的安装状态 |
| R12 | preview 与 apply 之间数据漂移 | **P0** | preview 计算 toCreate/toUpdate 后，apply 重新查询并直接覆盖 | preview 保存目标 revision；apply 使用预期 revision/CAS |
| R13 | 租户隔离缺失 | **P0** | run、run item 和五类标准实体匹配均未携带 tenant key | F1/T03 迁移必须明确租户键；旧数据按现场默认租户归档，不跨租户匹配 |
| R14 | 历史查询边界过宽 | **P1** | runs/list、runs/detail、builtin/list 无维护者权限表达式，且无租户过滤 | F2/F5 增加可见性策略与租户过滤 |
| R15 | 映射预检计数失真 | **P1** | mapping 行一律 `toCreate++`，但 apply 可能 UPDATE | T02 后续 preview contract 返回 create/update/conflict |
| R16 | 模板构建失败静默降级 | **P1** | ZIP 构建异常时返回单个数据元 CSV 字节，但仍使用 ZIP 下载名 | 移除静默降级，返回稳定错误码并记录失败审计 |
| R17 | 内置内容与后端镜像绑定 | **P1** | manifest 和 CSV 从 classpath 读取 | F4 改为独立内容仓库和可签名离线包 |

## 5. 本地修改被覆盖的最小复现

以下步骤可用现有单元测试 mock 或真实 PostgreSQL 重现，不需要构造 PJM 数据：

1. 首次安装 `common-data-elements`，产生 `APPLIED` run。
2. 在数据元页面把 `person_name` 的中文名称从包值修改为“客户人员姓名”，并修改说明。
3. 再次点击该内置包的“重新安装”。
4. preview 把该记录显示为 `toUpdate=1`，但不提示本地改写或冲突。
5. apply 调用 `metadataStandardService.update`，把名称和说明恢复为包值。
6. 若客户随后再次编辑，再回滚第 3 步的 run，当前实现仍会无 revision 校验地恢复第 3 步之前的 before-image。

该复现说明：before-image 能恢复“一次导入前的值”，不能证明升级不会覆盖客户修改，也不能安全处理导入后的继续编辑。

## 6. 当前内置内容规模

| 包 | 内容 |
|---|---:|
| `gbt-2261-gender` | 1 个目录、4 个码值 |
| `gbt-4658-education` | 1 个目录、16 个码值 |
| `gbt-2260-region` | 1 个目录、34 个码值 |
| `common-data-elements` | 3 个业务术语、15 个数据元 |

这些内容适合作为管道回归样例，不足以作为面向新部署的通用基线。

## 7. 测试覆盖审计

当前已有测试覆盖：

- 有效包预检、悬空 code set、码值行号、术语重复和无已知 CSV；
- 五类对象按依赖顺序创建；
- blocking preview、重复 apply、创建/更新回滚和幂等回滚；
- 内置包列表、计数、安装、未知 code 和阻断预检；
- 前端 API、三步向导、历史回滚、内置包入口和路由 source-contract。

当前缺少：

- 重新安装覆盖客户本地修改的显式回归测试；
- 术语 `DRAFT` 被强制改为 `ACTIVE` 的测试；
- preview/apply 间目标记录漂移、并发 apply/rollback；
- rollback 后实体已继续修改或被引用；
- 包版本、依赖、checksum、来源、许可和签名；
- 租户隔离、历史可见性、计量单位和真实 PostgreSQL 升级。

## 8. 兼容性决策

| 当前能力 | 决策 | 说明 |
|---|---|---|
| preview/apply/run/rollback API 路径 | 保持兼容 | v2 在响应中增量添加字段 |
| 01～05 CSV 列 | 保持兼容 | v2 增加 manifest 和第六类计量单位，不破坏旧包 |
| classpath v1 manifest | 通过适配器读取 | 只用于旧内置包迁移，不作为新内容发布格式 |
| 业务键匹配 | 仅作为旧记录识别线索 | v2 稳定身份改为 `packageCode + entityType + originRecordKey` |
| before-image 回滚 | 保留并加保护 | 必须叠加当前 revision、引用和冲突检查 |
| 术语强制 ACTIVE | 禁止延续到 v2 | v1 行为仅作为兼容风险明确展示 |
| `source_system` 表达标准来源 | 禁止 | 标准来源进入独立 provenance 模型 |
| classpath 大规模 CSV | 禁止扩展 | 后续内容由独立仓库构建为离线制品 |

## 9. T02/T03 入口

T02 必须先提供：

1. manifest v2 的稳定类型和错误码；
2. v1 classpath catalog 适配器；
3. 依赖拓扑、版本单调性和文件 SHA-256 校验；
4. preview 响应中的 package code/version/dependency/checksum 摘要。

T03 随后把包级身份落实到记录级 provenance，并为 F2 的三方 diff 保存基线 revision。没有 T03 前，不得批量扩充 DTS 内置内容。
