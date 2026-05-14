# T01: 契约校验支持正式 CSV

**优先级**: P0  
**状态**: DONE  
**依赖**: F1

## 目标

调整 metro-stack 契约校验逻辑：正式模式接受 `data_format=csv`，并校验 `data_uri=data.csv` 和文件存在。

## 技术设计

修改路径：

- `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/contract_validation.py`
- `/opt/prod/metro-app/sources/metro-stack/backend/tests/test_contract_validation.py`

规则：

- `data_format=csv` 为正式通过条件。
- `data_uri` 或 `csv_uri` 必须指向 `data.csv`。
- `schema.json` 的 feature 字段必须与 CSV 头匹配。
- `quality_report.json` 中 blocker/critical 失败项阻断训练。

## 验证

- [x] 单测：完整 CSV snapshot 返回 `passed`。
- [x] 单测：缺少 `data.csv` 返回 `blocked`。
- [x] 单测：CSV 头与 schema 不一致返回 `blocked`。

## 完成标准

- [x] demo contract 不再是默认训练入口。
- [x] 错误信息能指导用户回 DTS 重新导出快照。

## 完成记录

- 2026-05-14：`contract_validation.py` 正式接受 `data_format=csv`，目录模式校验 `data.csv` 存在并读取表头。
- 2026-05-14：测试命令 `PYTHONPATH=backend/src .venv/bin/python -m unittest discover -s backend/tests -p 'test_contract_*.py'` 通过，7 tests OK。
