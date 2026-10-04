# T01：实现 DRAFT 模型改型预检与追加 revision

**优先级**：P0  
**状态**：DONE
**依赖**：F1、F2

## 目标

让无实现证据的误建草稿在明确影响后安全调整模型类型，同时保留全部历史 revision。

## 技术设计（Contract-first）

- **输入契约**：当前 ModelSpec CAS、目标类型、可选业务维度引用和用户确认的清理字段。
- **Preview 请求**：`{targetType:ModelType, dimensionDefinitionRef?:{dimensionDefinitionId,revision}}`。
- **Preview 响应**：
  - `eligible:boolean`
  - `fromType/toType`
  - `targetLayer`
  - `retainedFields:string[]`
  - `requiredFields:string[]`
  - `clearFields:string[]`
  - `reasonCodes:string[]`
  - `currentRevision/checksum`
- **Apply 请求**：同 preview + `acceptedClearFields:string[]`、`idempotencyKey:string`，header `If-Match`。
- **输出契约**：preview 返回零写入差异；apply 返回同 modelSpecId 的新 canonical revision 和新 ETag。
- **Eligibility**：status=DRAFT；无 ModelImplementation；无 lifecycle event；无 release candidate；有写权限。
- **输出契约**：同 modelSpecId 新 revision；审计记录 from/to/revision；旧 revision 不变。
- **错误路径**：不符合=409 `MODEL_RECLASSIFY_NOT_ALLOWED`；preview 漂移=412；未确认清理字段=422；维度定义无效=422。
- **复用点**：账本 L15/L16；既有 ModelSpec CAS/revision/snapshot/audit。
- **实现方案**：服务端先 dry-run 类型专属投影，再事务性 append revision；不复用普通 update 的隐式清理。

## UI 交互规格

“调整模型类型”向导先重新展示四类含义，再展示 diff。财务示例必须提示：

- `TIME` 引用无效，改为 DIMENSION 后会清理；
- `项目名称` 需确认技术编码；
- 需要选择现行业务维度定义；
- r4 会保留。

## 影响范围

ModelSpecResource/ApplicationService/codec/repository、审计字典、详情页改型向导、tests。

## 验证（RED→GREEN）

- [x] preview 零写入
- [x] apply 幂等且追加 revision
- [x] 有 implementation/lifecycle/release 任一项即 fail closed
- [x] CAS 冲突不清空 UI 输入

## Definition of Done

- [x] 架构：历史和证据不被覆盖
- [x] UI：影响可理解且需显式确认
- [x] 切片：IT-09 隔离财务样本真实纠错通过，用户模型未修改
