# T01：keystore.p12 移出仓库与 tls 配置环境变量化

**优先级**：P0
**状态**：READY
**依赖**：无

## 目标

三个受 Git 跟踪的 `keystore.p12` 从仓库删除并加入忽略；`application-tls.yml` 去除明文口令与 classpath 私钥引用。

## 技术设计（Contract-first）

- **输入契约**：无（静态配置变更）。
- **输出契约**：见 F2 契约表——`SERVER_SSL_KEY_STORE` / `SERVER_SSL_KEY_STORE_PASSWORD` 两个 env key；未设置时 tls profile 启动显式失败（Spring 缺参报错），**不得**回退到任何内置文件或默认口令。
- **数据流**：无运行态数据流；构建产物中不再包含 p12。
- **错误路径**：误删后本地启用 tls profile 的开发者按 README/runbook 指引用 gen-certs.sh 生成（T02）；prod/dev compose 不激活 tls profile（账本#9），默认路径不受影响。
- **复用点**：`.gitignore` 既有条目风格；`application-tls.yml` 保持其余 ssl 参数（ciphers/protocols/http2）不变。
- **实现方案**：
  1. `git rm --cached` + 删除三个 `src/main/resources/config/tls/keystore.p12`（账本#7）；`target/classes` 下构建副本随重新构建消失，不入库。
  2. `.gitignore` 增加 `**/config/tls/keystore.p12` 与 `*.p12` 例外说明（保留 services/certs 的部署期产物不受 git 管理的事实——确认 services/certs 当前是否被跟踪，若被跟踪则本任务一并处理并记录）。
  3. `application-tls.yml`（dts-admin、dts-platform，账本#8）：`key-store` 改 `${SERVER_SSL_KEY_STORE:}`，`key-store-password` 改 `${SERVER_SSL_KEY_STORE_PASSWORD:}`；dts-common 无 application-tls.yml，仅删 p12。
  4. 同步删除/更新引用 classpath keystore 的注释与文档（JHipster 生成头注释可保留但需注明外部注入）。
- **禁止**：把新 p12 重新提交；在 yml 中保留任何形式的兜底口令。

## 影响范围

- `source/dts-admin/src/main/resources/config/tls/keystore.p12`（删除）
- `source/dts-common/src/main/resources/config/tls/keystore.p12`（删除）
- `source/dts-platform/src/main/resources/config/tls/keystore.p12`（删除）
- `source/dts-admin/src/main/resources/config/application-tls.yml`、`source/dts-platform/src/main/resources/config/application-tls.yml`
- 根 `.gitignore`

## 验证（RED→GREEN）

- [ ] `git ls-files | grep -iE "keystore|\.p12$"` 无命中。
- [ ] `grep -rn "key-store-password: password" source/` 无命中。
- [ ] dts-admin/dts-platform 默认 profile 构建与启动不受影响（quality-gate 最小集）。

## Definition of Done

- [ ] 架构：仓库零私钥材料；默认 profile 构建绿
- [ ] UI：无
- [ ] 切片：证据（git ls-files 输出、构建日志摘要）入 `it/evidence/it-03-tls-keystore/`
- [ ] 无占位证据
