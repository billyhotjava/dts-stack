# T01: 提供版本钉定的生成 dbt 只读预览

**优先级**: P0
**状态**: IN_PROGRESS（源码与聚焦自动化完成；真实 API/browser IT 待补）
**依赖**: F0/T02（确认编译产物的真实文件与类型集合）、F1/T01 的模式访问契约

## 目标

为 DESIGNER_GENERATED 模型提供无副作用、版本钉定的 dbt SQL/SCHEMA 预览，供代码模式展示和后续接管摘要使用。

## 技术设计 (Contract-first)

- **输入契约**: path `modelSpecId:UUID`；query `modelRevision:int>0`、`implementationRevision:int>0`；当前用户须有 `CATALOG_MAINTAINERS`。
- **输出契约**: `DbtImplementationPreviewView`：
  - `modelRevision:int`、`modelChecksum:sha256`；
  - `implementationRevision:int`、`implementationChecksum:sha256`；
  - `ownership:'DESIGNER_GENERATED'`、`readOnly:true`；
  - `files:[{path:string,content:string,checksum:sha256,nodeKind:'MODEL'|'STG',artifactTypes:string[]}]`；
  - `previewChecksum:sha256`。

  **`files` 必然是 3 项**：`stg_<name>.sql`（ephemeral，`nodeKind='STG'`）、`<name>.sql`、`<name>.yml`（账本 #16）。前端据 `nodeKind` 区分系统 STG 与主模型。`artifactTypes` 映射须从 `CanonicalModelLifecycleCompilerAdapter` 最小抽取为共享纯函数，使预览与正式 compile 共用同一 owner；不得复制 private 方法规则。
- **数据流**: 按 tenant/model/revision 读取 current ModelSpec + implementation → 校验 ownership/ETag pin → 调用既有 `ModelingDbtCompiler` → 规范化并计算 checksum → 返回，不保存 artifact/lifecycle event。
- **错误路径**:
  - 不存在/跨租户 → 404；越权 → 403；
  - revision/checksum 已变化 → 409 `MODEL_DBT_PREVIEW_STALE`；
  - 非 DESIGNER_GENERATED → 409 `MODEL_DBT_PREVIEW_DESIGNER_REQUIRED`；
  - 编译门禁未满足 → 422，透传稳定 diagnostic code，不返回部分伪代码。
- **复用点**: `ModelingDbtCompiler`、ModelSpec/Lifecycle repository、现有 artifact normalization；禁止复制 SQL 生成逻辑。
- **容量**: 输出遵守 draft 的 128/2MiB/16MiB 边界；超过即 422，不截断代码。

## Definition of Ready

- [x] F0/T02 已冻结 3 文件预览与 artifact type 映射契约。
- [x] F1 code view 和 TECHNICAL capability 已可承载只读预览。
- [x] preview pins/checksum、权限和零写入边界已定义。

## 影响范围

- 新增薄 REST resource/contract 或在现有 lifecycle resource 下扩展 endpoint
- `ModelingDbtCompiler` 只作为复用依赖，不改变编译语义；artifact path/type 映射仅做最小公共抽取
- 前端 API/TS contract
- focused service/MockMvc tests

## 验证 (RED→GREEN)

- [ ] 同一 revision 重复请求得到相同 previewChecksum，数据库行数不变。
- [ ] `files` 恰为 3 项，且恰有一项 `nodeKind='STG'`。
- [ ] model/implementation 任一 revision 改变时旧请求失败。
- [ ] 非 DESIGNER、越权、跨租户、超限和编译门禁失败有精确断言。
- [ ] 静态检查/spy 证明未调用 saveArtifacts、recordEvent 或 release API。

## Definition of Done

- [ ] 预览只读、无副作用、版本和容量受控。
- [ ] 前后端契约类型一致，错误码已进入测试。
- [ ] IT-02 可用真实 DESIGNER 样本验证。
