# T01: 盘点modern运行时、构建与测试依赖

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

确认 `dts-analytics-webapp/modern` 当前是否真的“没用可以直接删”，并把所有阻断删除的依赖链显式列出。

## 技术设计

- 盘点至少以下依赖面：
  - compose 服务定义
  - dev-up/dev-stop 启停脚本
  - `builds/dts-build.sh`
  - `dts-platform-webapp` 的 dev proxy 和入口兼容
  - `tests/web-e2e`、shell 测试、发布文档
- 输出结论：
  - 若仍有运行时依赖，则不能直接删，只能先切断依赖再删除。
  - 若仅剩文档引用，可直接进入删除任务。

## 影响范围

- `docker-compose*.yml`
- `dev-up.sh`
- `dev-stop.sh`
- `builds/dts-build.sh`
- `source/dts-platform-webapp/vite.config.ts`
- `tests/**`
- `docs/**`

## 验证

- [ ] 依赖清单能说明为什么 `modern` 当前不能直接删除
- [ ] 每一项依赖都能对应到一个后续拆除任务

## 完成标准

- [ ] 删除前置门禁清单完成
- [ ] 是否可直接删除有明确结论
