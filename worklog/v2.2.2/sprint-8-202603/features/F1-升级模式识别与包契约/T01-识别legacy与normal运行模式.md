# T01: 识别 legacy 与 normal 运行模式

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标
定义升级器如何基于旧目录 `.env`、`LEGACY_STACK`、compose 文件存在性识别现场运行模式。

## 技术设计
- 优先读取旧目录 `.env`
- 当 `LEGACY_STACK=true` 时进入 legacy 升级分支
- legacy 分支以 `docker-compose.legacy.yml` 为运行态主文件
- normal 分支继续使用 `docker-compose.yml` 和 `docker-compose-app.yml`

## 影响范围
- `bin/dts-upgrade`
- 升级设计文档
- 升级 runbook

## 验证
- [x] legacy 目录能识别为 legacy 模式
- [x] normal 目录能识别为 normal 模式

## 完成标准
- [x] 升级器模式识别规则清晰可执行
- [x] 不再把 legacy compose 当成普通参考文件
