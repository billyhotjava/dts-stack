# F2：TLS 私钥出库与部署期注入

**优先级**：P0
**状态**：READY

## 目标

源码仓库不再携带任何 TLS 私钥材料；tls profile 激活时私钥与口令经部署期产物（`services/certs`）与环境变量注入；Git 历史中已泄露的旧私钥完成轮换。

## 契约定义（Contracts）

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 配置 | `application-tls.yml`（dts-admin / dts-platform） | `key-store: ${SERVER_SSL_KEY_STORE:}`（外部文件路径，无 classpath 默认）；`key-store-password: ${SERVER_SSL_KEY_STORE_PASSWORD:}`（无默认值） |
| 交付 | Git 跟踪文件 | `git ls-files` 无 `*.p12`；`.gitignore` 增加 keystore 排除条目 |
| 部署 | `services/certs/keystore.p12` + gen-certs.sh | init.sh/部署期生成；口令写入 `.env` 的 `SERVER_SSL_KEY_STORE_PASSWORD` 并由 compose 传入 |
| 运维 | 私钥轮换 | 重跑 gen-certs.sh → 重建挂载 → 重启对应服务；旧私钥作废声明 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | keystore.p12 移出仓库与 tls 配置环境变量化 | P0 | READY | - |
| T02 | 部署期证书生成与注入链路打通 | P0 | READY | T01 |
| T03 | 已泄露私钥轮换与升级说明 | P0 | READY | T01/T02 |

## Definition of Ready

- [x] 契约已钉死（env key 命名、文件路径、gitignore 条目）
- [x] 竖切片已画通（源码出库 → 部署期生成 → compose 注入 → profile 激活可用）
- [x] UI 落点：本 Feature 无 UI
- [x] 依赖已就绪（部署期证书链已存在，账本#10；tls profile 当前休眠，账本#9——变更面可控）
- [x] 验收可验证（IT-03）

## 完成标准

- [ ] `git ls-files | grep -i "keystore\|\.p12"` 无命中（IT-03）
- [ ] 无 `key-store-password: password` 明文残留（IT-03）
- [ ] tls profile 以 env 注入启动验证通过；未注入时按预期失败而非使用默认口令（IT-03）
- [ ] 旧私钥轮换完成并有记录（IT-03）
