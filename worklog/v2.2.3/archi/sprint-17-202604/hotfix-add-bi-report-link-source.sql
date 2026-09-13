-- Sprint-17 emergency backfill — bi_report_link.source 列缺失补丁
--
-- 触发条件：升级到 sprint-17 / hotfix 后，打开"我的概览"报错
--   ERROR: column brl1_0.source does not exist
-- 说明 Liquibase changeset `20260425_02_bi_report_link_source.xml` 没在
-- 生产库执行过（可能是 jar 旧 / Liquibase 被禁用 / changelog 标记被改）。
--
-- 本脚本幂等：可重复执行不会报错。运行后下次启动 Liquibase 会通过
-- precondition (columnExists=true) 把这条 changeset 自动 MARK_RAN，
-- 无需手动改 databasechangelog。

\echo '== Sprint-17 hotfix: add bi_report_link.source =='
ALTER TABLE bi_report_link
    ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

CREATE INDEX IF NOT EXISTS idx_bi_report_link_source
    ON bi_report_link (source);

-- 验证
\echo '== 验证 source 列 =='
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'bi_report_link' AND column_name = 'source';

\echo '== 验证索引 =='
SELECT indexname FROM pg_indexes
WHERE tablename = 'bi_report_link' AND indexname = 'idx_bi_report_link_source';

\echo '== 完成。重启 dts-platform 即可。 =='
