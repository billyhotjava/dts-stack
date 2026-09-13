# T04: security-reviewer 全量复审

**优先级**: P0
**状态**: READY
**依赖**: T01-T03

## 目标

对 F1/F2 全部加密敏感改动做安全复审，无 CRITICAL/HIGH 方可准入。

## 复审清单（RED — 逐项必须通过）

- 加密正确性：AES-GCM、IV 唯一不复用、tag 校验、密钥不硬编码（仅来自 `DTS_INFRA_ENCRYPTION_KEY`）。
- 密钥处理：密钥不进日志/不进异常消息/不进审计明文；keyVersion 路由正确。
- 明文生命周期：明文仅 tmpfs、0600、作业即焚；无任何 bind/磁盘明文写入路径。
- 无回退：密钥缺失/解密失败不退化为明文（F1-T04、F2-T04 验证）。
- 注入与替换：job.json 路径改写不引入注入（路径来源可信、转义正确）。
- 错误屏蔽：诊断信息不泄露明文片段、密钥、内部路径。
- 依赖：tmpfs noexec、size 限制；POSIX 权限 0600。

## 技术设计（手段）

- 调用 security-reviewer 子代理复审 F1/F2 diff。
- 结合 `gitnexus_detect_changes()` 核对改动范围与预期一致（仅 FileUploadService / AddaxEnvRunner / AirflowDagService / crypto 工具 / compose）。

## 影响范围

- 复审对象：F1/F2 全部改动文件
- 产出：`it/evidence/security-review/sprint37-review.md`

## 验证

- [ ] 复审清单逐项通过。
- [ ] 无 CRITICAL/HIGH。

## 完成标准

- [ ] 安全复审签署，允许准入。
