# T01: Threat Model 与 Secret Policy

**优先级**: P0
**状态**: DRAFT
**依赖**: F1/T02, F1/T05

## 目标

识别 API 接入带来的安全风险，并制定密钥、网络、日志、响应样本和任务产物的保护策略。

## 范围

- 风险：SSRF、密钥泄露、日志泄露、响应样本含敏感数据、越权调用、mTLS 私钥保护。
- 策略：URL allowlist/denylist、协议限制、secretRef、脱敏、响应截断、审计。
- 明确 preview 和 execute 的安全边界。

## 完成标准

- [ ] Threat model 文档评审通过。
- [ ] URL 和协议安全策略可配置。
- [ ] 敏感值泄露测试进入发布门禁。
- [ ] mTLS/private key 有明确存储边界。

