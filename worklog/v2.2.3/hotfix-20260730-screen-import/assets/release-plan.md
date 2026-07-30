# 数据大屏 JSON 导入修复发布安全计划

**变更类型**：服务间鉴权白名单修复 + HTTP 错误契约修复  
**风险等级**：高（涉及密级控制面与服务鉴权）

## 1. 迁移策略

本次无数据库结构变更、无数据回填、无删除或重命名。

## 2. 兼容性

| 消费方 | 影响 | 处置 |
|---|---|---|
| `dts-analytics` 密级契约客户端 | 原调用被平台端白名单误拒绝 | 仅放行客户端实际使用的 5 个方法/路径组合 |
| 数据大屏 JSON 导入 | 原 409 被统一异常处理器包装成 500 | 保留 `ResponseStatusException` 的原状态、原因与不可重试语义 |
| 其他平台内部服务 | 不应受影响 | 既有服务名、token 校验和其他路径白名单保持不变 |

## 3. 部署顺序

1. 为当前 `dts-platform:1.0.0`、`dts-analytics:1.0.0` 镜像建立回滚标签。
2. 先替换 `dts-platform`，确认健康和密级推导契约不再返回 401。
3. 再替换 `dts-analytics`，确认健康、409 状态映射和 JSON 导入。

## 4. 回滚

- 平台回滚镜像：`dts-platform:rollback-screen-import-20260730`
- 分析服务回滚镜像：`dts-analytics:rollback-screen-import-20260730`
- 回滚方式：将相应回滚镜像重新标记为 `1.0.0`，再仅重建对应 Compose 服务。
- 本次无不可逆数据操作；创建失败事务保持回滚。
- 发布前验证回滚标签与当前运行容器镜像 ID 一致。为避免额外生产抖动，不执行容器级来回切换演练，此项记为 GAP。

## 5. 验收闸门

- [x] 平台鉴权回归测试通过，并证明相邻 `/explain` 端点仍不开放给 `dts-analytics`。
- [x] 分析服务异常映射回归测试通过。
- [x] 两个服务健康检查通过。
- [x] 在线只读探针已穿过服务鉴权层；密级守卫进入业务判断，不再返回 401。
- [ ] 原 `gpmc-overview-v4.json` 创建成功并生成密级快照：等待当前登录用户在页面重新点击“确认导入”，不冒用无法确认的用户身份执行写入。

## 6. 发布结果

- `dts-platform`：运行镜像 `sha256:74fc3945b95e21e463c9d1742ed7c159bae379ffbb427274f9da357054ae6a39`，健康。
- `dts-analytics`：运行镜像 `sha256:a722fbbbe300ffd7541dc4cd1d24744e222ec6cbfedacdc06064fe1b9911d279`，健康。
- 平台回滚镜像：`sha256:0c170ce1adef0ce3a4f0644c5c1ac271b5d0d6986df3af2d0aefbbfee77de0ce`。
- 分析服务回滚镜像：`sha256:592e10d34cb22d63a56dca601d8c9023e5d5f324ce38a66b4c2ec910831ca68f`。
- 在线探针：`GET /api/catalog/classifications/consumers/guard` 返回业务层“Consumer classification has not been derived”，证明服务鉴权已放行；该探针不创建或修改业务数据。
