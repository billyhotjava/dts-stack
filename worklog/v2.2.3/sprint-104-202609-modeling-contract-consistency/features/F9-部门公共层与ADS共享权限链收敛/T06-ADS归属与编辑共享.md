# T06：ADS 归属与编辑共享

**优先级**：P1
**状态**：DRAFT
**依赖**：T01（K79–K81、K86 冻结）、T05（部门维护范围）；Q2 已确认归属不转移，Q6 已确认被授权人解析方案

## 目标

ADS 归创建人：同部门其他建模人员默认只读；创建人可把编辑权授给同部门建模人员或部门级建模角色，也可撤销；部门领导与所级角色始终可管理；授权与撤销即时生效并审计。

## 使用场景

- **S03**：B 打开 A 创建的 ADS“项目月报” → 可查看定义、实现与交付状态；编辑控件只读，提示“编辑需创建人授权”。
- **S04**：A 点击“共享”，搜索并添加 B 为可编辑 → 201；B 刷新后可编辑保存。
- **S05**：A 撤销 B；B 未刷新直接保存 → 403 `MODEL_EDIT_GRANT_REQUIRED`，输入保留；刷新后只读。
- **S06**：A 尝试授权普通员工 C → 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`。
- **S07**：部门甲领导 L 修改 A 未共享的 ADS → 允许，审计标注管理权。
- **角色授权**：A 授予角色“部门数据管理员” → 部门甲全部数据管理员可编辑；部门乙数据管理员不受影响。
- **跨部门**：A 尝试授权部门乙数据管理员 D → 422 `MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL`，抽屉显示“申请跨部门共享（待开放）”。
- **S13**：A 调岗后失去该 ADS 编辑权；归属人仍显示 A 且不转移（Q2 已确认）；部门甲领导凭管理权维护。
- **非 ADS**：对 DWD 调用授权接口 → 422 `MODEL_ACCESS_LAYER_NOT_SHAREABLE`，界面不显示“共享”。

## 技术设计与契约

- **契约引用**：C76、C81、C84、C90、C91、K79、K80、K81、K84、K86。
- **数据契约**：
  - `modeling_model_spec.owner_id varchar(128)`：前向增列；APPLICATION 模型以 r1 `created_by` 回填；新建 ADS 写入当前 actor；非 ADS 为 null。
  - `modeling_model_access`：`id uuid pk`、`tenant_id varchar(128) not null`、`model_spec_id uuid not null fk`、`grantee_type varchar(8) check in ('USER','ROLE')`、`grantee_id varchar(128) not null`、`permission varchar(16) check = 'EDITOR'`、`granted_by varchar(128) not null`、`granted_at timestamp not null`、`revoked_by varchar(128)`、`revoked_at timestamp`；部分唯一 `(tenant_id, model_spec_id, grantee_type, grantee_id) where revoked_at is null`；索引 `(tenant_id, grantee_type, grantee_id) where revoked_at is null`。
- **接口契约**：
  - `GET /api/modeling/model-specs/{id}/access-grants` → `ApiResponse<[{id, granteeType, granteeId, granteeName, permission, grantedBy, grantedAt}]>` 与 `canManage:boolean`。
  - `POST /api/modeling/model-specs/{id}/access-grants`，body `{granteeType:"USER"|"ROLE", granteeId:string, permission:"EDITOR"}` → 201 新记录；已存在有效记录 → 200 原记录。
  - `DELETE /api/modeling/model-specs/{id}/access-grants/{grantId}` → 204；已撤销 → 204 幂等。
  - 错误：403 `MODEL_ACCESS_MANAGE_DENIED`；422 `MODEL_ACCESS_LAYER_NOT_SHAREABLE`、`MODEL_ACCESS_GRANTEE_NOT_AUTHOR`、`MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL`；ROLE 只接受 `ROLE_DEPT_DATA_OWNER`、`ROLE_DEPT_LEADER`，其他 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`；不可见模型 404。
- **判定契约**：新增 `ModelSpecWriteAccessPort.canEdit(tenant, modelSpecId, actor)`：非 APPLICATION → K75；APPLICATION → 所级角色 ∨ (部门领导 ∧ 同部门) ∨ (具备 K73 角色 ∧ 同部门 ∧ (owner ∨ 有效 USER 授权 ∨ actor 角色命中有效 ROLE 授权))。拒绝 403 `MODEL_EDIT_GRANT_REQUIRED`，审计 `MODEL_EDIT_GRANT_DENIED`。管理授权 = 所级角色 ∨ (部门领导 ∧ 同部门) ∨ owner；不提供归属转移接口（Q2）。
- **数据流**：
  - 编辑：工作台保存 → 接口角色准入（T03）→ `canEdit`（本任务，替代以模型为对象的写操作中的 `canMaintain`）→ 既有写入 → 审计。
  - 共享：抽屉添加 → 授权接口 → 校验管理权、层级、被授权人角色与部门（按 K86：复用 `AdminUserDirectoryClient` 解析被授权人部门与角色，目录授权的请求体透传不可采信，见 C90/C91；目录不可用返回 503 并拒绝授权）→ 写授权表 → 审计 `MODEL_ACCESS_GRANT/REVOKE`。
  - 展示：`authoring-context` 与 `delivery-status` 的 allowedActions/wizard 按 `canEdit` 输出，只读原因 `MODEL_EDIT_GRANT_REQUIRED`；模型列表批量计算“我的权限”，不逐行查询。
- **错误路径**：撤销后已打开页面的写请求 403 并保留输入；被授权人角色在授权后被收回 → `canEdit` 实时按 K73 判定为拒绝；并发授予同一人 → 部分唯一约束冲突转 200 返回已有记录；模型归档后授权保留但编辑沿用归档只读。
- **复用点**：计划级操作（执行绑定、运行健康、质量补跑等计划对象）保持 `canMaintain`；以模型为对象的写操作切换到 `canEdit`，C76 中的具体切换清单由 T01 冻结；被授权人解析复用目录授权既有入口，不建用户目录副本；大屏 ACL 只作语义参考，不共用表。
- **实现方案**：K86 目录角色字段扩展（dts-admin 响应 + 平台客户端）→ 迁移（增列、回填、建表）→ 端口与适配器 → 按 T01 清单替换调用 → 授权接口与审计 → allowedActions/列表权限批量计算 → 前端共享抽屉与只读提示。建模为本版本新增，回填只涉及测试环境数据。

## UI 交互

- 入口：ADS 工作台工具栏“共享”按钮，仅 `canManage=true` 时显示；非 ADS 不显示。
- 抽屉：成员搜索只返回同部门具备建模角色的用户和两个部门级建模角色；列表显示成员、权限、授予人、时间与“撤销”；固定显示“部门领导（管理权，默认拥有）”；底部禁用按钮“申请跨部门共享（待开放）”。
- 只读：无编辑权时工作台各步骤主动作隐藏，页头提示“你可以查看此模型；编辑需创建人授权”，并显示归属人。
- 列表：“我的权限”列显示“可编辑/只读/管理”。
- 四态：抽屉空（尚未共享）/加载（禁重复添加）/错误（按错误码文案，保留搜索输入）/成功（刷新列表并提示）。

## 影响范围

新 changeSet；dts-admin 目录响应与平台目录客户端（K86）；`ModelSpecWriteAccessPort` 及适配器；T01 清单内以模型为对象的写服务；`ModelSpecResource`（授权接口）；`ModelAuthoringDraftService.context`、`ModelDeliveryStatusQueryService` allowedActions；模型列表查询；dts-admin 审计字典；前端工作台工具栏、共享抽屉、只读提示、列表列。编辑前对端口与每个调用方运行 GitNexus impact 并报告。

## 验证与验收

- RED：同部门数据管理员当前可直接修改他人 ADS；授权接口不存在。
- GREEN：S03–S07、角色授权、跨部门授权、非 ADS 授权、撤销即时生效、归档、并发授予逐一通过；回填后存量 ADS 归属人与 r1 创建人一致；列表权限计算 SQL 次数不随行数线性增长。
- 映射 **IT-61**。

## Definition of Ready

- [ ] K79–K81、K86 冻结（Q2、Q6 已确认），C76 切换清单冻结。
- [ ] T05 已落地；T01 已确认现场 ADS 创建人回填可行。
- [ ] GitNexus impact 已报告用户。

## Definition of Done

- [ ] 迁移在清洁库与升级库验证，回填对照留证。
- [ ] 判定矩阵、授权接口、撤销、并发专项测试通过，C76 调用方回归无失败。
- [ ] IT-61 留存各场景请求/响应、授权表前后数据、审计与 Chrome 95 截图。
