# T03: 清理compose、build、test对modern webapp的依赖

**优先级**: P0  
**状态**: READY  
**依赖**: T01,T02

## 目标

把 `modern` 从 compose、镜像构建、测试编排中移除，确保仓库基础设施不再把它视为必需前端服务。

## 技术设计

- 删除或改写：
  - `docker-compose-app.yml`
  - `docker-compose.dev.yml`
  - `docker-compose.legacy.yml`
  - `dev-up.sh`
  - `dev-stop.sh`
  - `builds/dts-build.sh`
  - `tests/web-e2e/playwright.config.ts`
  - 相关 shell 测试与说明文档
- 统一让 e2e 和本地 smoke 以 platform 入口运行 analytics 场景。

## 影响范围

- `docker-compose*.yml`
- `builds/dts-build.sh`
- `dev-up.sh`
- `dev-stop.sh`
- `tests/**`
- `docs/release/**`

## 验证

- [ ] compose 启停不再包含 `dts-analytics-webapp-modern`
- [ ] 构建脚本不再产出 `dts-analytics-webapp-modern` 镜像
- [ ] e2e/dev server 不再启动 `source/dts-analytics-webapp/modern`

## 完成标准

- [ ] modern webapp 退出基础设施编排
- [ ] 测试与发布脚本完成切换
