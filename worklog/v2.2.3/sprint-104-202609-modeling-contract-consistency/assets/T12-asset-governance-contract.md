# T12 资产基本治理局部维护契约

**状态：FROZEN；仅 owner 与 description，目录仍为唯一写 owner。**

`PATCH /api/catalog/datasets/{id}/governance-summary`

请求为 `{ "owner": string|null, "description": string|null }`，必须携带 `If-Match: "catalog-dataset:{id}:{version}"`。成功返回 `ApiResponse<CatalogDataset>` 与新的 ETag；缺失 precondition 为 428，ETag 格式/资产身份/旧 version 不匹配均为409，非法 PATCH 字段或值类型为400，隐蔽或无权限资产继续使用既有 not-found/permission 边界。

端点只修改 `owner`、规范化后的 `description` 和审计字段。它不得接收或修改分类、标签、ownerDept、名称、domain、source/schema/table、lifecycle 或模型身份。读取和写入均复用 `CatalogDatasetResource` 的 `ensureDatasetEditPermission` 与 `AssetAction.UPDATE`；不创建模型服务中的资产副本或关系表。

`catalog_dataset.version` 是共享乐观锁版本：前向 changeSet 增列并回填零，实体 `@Version` 使完整 PUT、同步和本 PATCH 都参与同一 CAS。完整 PUT 也必须携带相同 If-Match；旧客户端缺失该头返回428，须先读取当前 ETag，避免延迟旧 PUT 在 PATCH 提交后重新读取新版本又覆盖 owner/description。重新读取同一 dataset 证明目录入口与向导入口一致。
