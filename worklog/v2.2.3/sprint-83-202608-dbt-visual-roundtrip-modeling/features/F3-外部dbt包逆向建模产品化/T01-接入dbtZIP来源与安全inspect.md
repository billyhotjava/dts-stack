# T01：接入 dbt ZIP 来源模式与安全 inspect

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0/T02、F1/T02

## 目标

在逆向向导中上传 dbt 项目 ZIP 并调用现有 archive inspect，展示真实项目信息和安全/兼容问题。P0 不提供 Git 接入或其他包来源。

## Contract-first

- **UI**：dbt 逆向分支只有 `DBT_ARCHIVE` 上传；现有数据库表/视图逆向能力不属于本 Feature，且不得借此新增 Git 选项。
- **输入**：multipart `archive`；不得附带 profiles/credentials。
- **输出**：现有 `ModelPackageJson`（package/project checksum、models/sources/technicalNodes/issues）以及 `inspectionProof/proofExpiresAt`。proof 首期固定 30 分钟有效，签名绑定 proof/canonicalization version、tenant、canonical actor、规范化技术包 checksum、issuedAt/expiry。
- **错误路径**：超限 413、恶意/无效 ZIP 400、不支持结构 422、越权 403；临时文件必须清理。proof 的 invalid/expired 在 preview 分别稳定为 409 `DBT_IMPORT_INSPECTION_PROOF_INVALID`、410 `DBT_IMPORT_INSPECTION_PROOF_EXPIRED`。
- **供应链边界**：不得接收 Git URL/token/SSH key，不执行 clone/fetch/pull/push，不在线下载 packages，不执行模型 SQL。
- **保留边界**：原始 ZIP 仅在受控临时区完成 inspect；inspect 不新建会话/台账，也不持久化原始包。preview 验证 proof 后才把规范化快照、checksum 和必要不可变 artifact 固定到现有 import run/item，不把原始压缩包变成第二套事实源。
- **签名边界**：复用平台托管的签名/密钥能力；禁止在导入模块硬编码、增加独立应用 secret 或建立第二套密钥台账。proof、ZIP、SQL/Jinja、secret 与签名密钥正文不得写普通日志或公共审计。
- **复用**：`inspectDbtModelArchive`、SafeZipExtractor、DbtModelArchiveInspectService。

## 验证

- [ ] FX-01～03 成功/阻断符合契约；FX-05 全部拒绝且无残留。
- [ ] 篡改技术包、跨 tenant/actor 重放、未知 proofVersion、伪造签名均为 INVALID；时钟超过 30 分钟为 EXPIRED；两类失败均不创建 import run。
- [ ] 前端不再从 `DISCOVERED_MODELS` 产生 dbt 结果。

## Definition of Done

- [ ] inspect 无模型 SQL 执行、无在线下载、无业务落库。
- [ ] inspect → preview 的可信交接符合 D13，且客户端即使重算公开 checksum 也不能绕过签名验证。
- [ ] 产品/API 均不存在 Git 凭据、远程仓库同步或在线 packages 下载路径。
