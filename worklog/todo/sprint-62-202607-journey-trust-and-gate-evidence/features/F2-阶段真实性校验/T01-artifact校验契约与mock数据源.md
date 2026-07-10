# T01: artifact 校验契约与 mock 数据源

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
定义"参数是否指向真实对象"的校验契约，先用 mock 数据源实现，可无缝替换真实 API。

## 技术设计
- 新建 `src/components/journey/journeyArtifactValidation.ts`：
  - `ArtifactValidationStatus = "valid" | "invalid" | "unknown"`。
  - `ArtifactValidationResult = { key: JourneyContextParamKey; value: string; status; reason?: string; apiName?: string }`。
  - `ArtifactValidator = (key, value) => ArtifactValidationStatus | Promise<...>`；同步内核 `resolveArtifactValidations(params, validator)` 纯函数。
  - mock validator：内置合法 id 前缀/清单（与现有 mock 契约对齐，如 `src/api` 下 mock 数据的 id 规则，读现有 mock 再定）；未覆盖的 key 返回 unknown 并带 `apiName`（如 `GET /api/models/{id}`）。
- 不发真实请求；异步能力仅保留类型位。

## 影响范围
- 新增 `journeyArtifactValidation.ts` + `JourneyArtifactValidation.source-contract.test.ts`
- `index.ts` 导出

## 验证
- [ ] 三态输出、未提供参数跳过、validator 抛错归 unknown。

## 完成标准
- [ ] 纯函数可测零 React 依赖；测试 GREEN；build 通过。
