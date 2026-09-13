# Sprint-68 IT 与交付证据计划

**状态**：READY

## 1. 验收旅程

### Journey A：新环境安装通用企业版

1. 全新租户进入标准内容库；
2. 选择“通用企业版”，查看将安装的包、依赖、版本、来源和条目统计；
3. 执行 preview，确认没有阻断错误；
4. apply 后核对术语、数据元、公共码表和计量单位真实数据库计数；
5. 从数据元进入模型字段绑定，确认稳定 ID/版本可用。

### Journey B：客户本地改写

1. 修改一个基线数据元的中文名、长度或安全等级；
2. 新增一条客户数据元和一张内部码表；
3. 页面明确显示“本地改写”和“客户扩展”；
4. 数据库保留 baseline revision 与 override diff；
5. 普通查看者不能伪造写请求。

### Journey C：内容升级与三方合并

1. 安装包 v1；
2. 本地修改记录 A；
3. 使用 v2 同时变更 A、更新 B、新增 C、弃用 D；
4. preview 展示自动更新、保留本地、冲突、新增和弃用；
5. 冲突未处理时 apply fail closed；
6. 人工选择后升级成功，引用 D 的对象仍可读并得到替代提示。

### Journey D：升级回滚

1. 对 Journey C 的升级 run 执行回滚；
2. B/C/D 恢复到 v1 生效状态；
3. A 的客户本地改写继续保留；
4. 客户扩展记录不被删除；
5. 审计可还原操作者、包版本和选择。

### Journey E：PJM 候选准入

1. 扫描 PJM/dbt 指定事实源；
2. 证明 target/logs/test/BI/ZIP/backup 被硬排除；
3. 对 Canonical dim、Alias dim、数据元候选生成 ACCEPT/CONDITIONAL/REJECT 报告；
4. 证明 DRAFT 模型和 warn 级测试不能自动晋升；
5. 构建 `pjm-project-management-reference` 时只包含人工批准项；
6. 与通用基线执行稳定键和语义去重。

### Journey F：离线部署与失败恢复

1. 断网环境从产品发布包加载本地内容目录；
2. 缺依赖、签名错误、checksum 错误、许可证未批准分别阻断；
3. 安装中事务失败不产生半包数据；
4. 远程目录不可达不影响离线包安装；
5. Chrome 95 桌面和 390px 可完成目录、预检、安装和冲突处理。

## 2. 自动化矩阵

| 层级 | 必测内容 | 证据目录 |
|---|---|---|
| Contract | manifest v2、六 CSV、稳定键、依赖、版本、来源 | `it/evidence/contracts/` |
| Content CI | 许可、checksum、重复、空值、引用、客户词/测试词扫描 | `it/evidence/content-ci/` |
| Backend | preview/apply/upgrade/conflict/rollback/tenant/audit | `it/evidence/backend/` |
| PostgreSQL | 首装、重复安装、三方合并、本地覆盖、弃用、回滚 | `it/evidence/postgresql/` |
| Frontend | 内容目录、画像、冲突、本地改写、错误恢复 | `it/evidence/frontend/` |
| Chrome 95 | 桌面、390px、权限、刷新、失败态 | `it/evidence/chrome95/` |
| Build | dts-platform、dts-platform-webapp、离线内容制品 | `it/evidence/build/` |
| Scope | GitNexus 变更流、无意外模块 | `it/evidence/gitnexus/` |

## 3. PJM 准入必测负例

- `models.tsv` 状态为 DRAFT；
- 唯一/非空/accepted-values 仅为 warn；
- `dim_boolean_alias` 的“已完成→是”；
- `dim_risk_category_alias` 的有损分类归并；
- context 丢失的签署状态映射；
- ODS 全 varchar 被直接照搬为数据元类型；
- `target/`、`logs/`、`test/`、screen JSON、图片、ZIP、`.bak`；
- DWS/ADS 指标被错误导入业务术语或数据元；
- 具体项目号、人员、部门、供应商或测试金额值进入内容包。

## 4. 内容包必测边界

- 包版本回退、重复版本、跳跃版本；
- 依赖缺失、循环依赖、最低版本不满足；
- 来源 URL 缺失、许可证为 UNKNOWN/REJECTED；
- 同包稳定键重复、跨包未声明覆盖；
- 数据元引用不存在的码表或单位；
- 单位基准引用不存在、跨量纲换算、换算循环；
- 本地改写与上游同字段冲突；
- 被引用记录弃用、替代项缺失；
- 跨租户安装、升级或读取；
- ETag/CAS 冲突、重复提交和回滚幂等。

## 5. 完成真实性

- README 只索引真实生成的日志、JSON、SQL 对账和截图。
- mock 浏览器只能证明交互契约，不能替代真实 API/PostgreSQL。
- 条目数、包数和页面显示“已安装”不能替代来源合法性与客户覆盖安全。
- F6-T04 最终报告必须分别给出代码、测试、内容、部署、浏览器五层结论。
