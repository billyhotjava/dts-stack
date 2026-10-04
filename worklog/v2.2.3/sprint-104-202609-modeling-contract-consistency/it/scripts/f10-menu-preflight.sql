-- Sprint-104 F10 升级前只读核对：菜单排序迁移 20260916-03 的定位键唯一性、父节点与大屏管理状态。
-- 用法：psql -d dts_admin -v ON_ERROR_STOP=1 -f f10-menu-preflight.sql
-- 判定：第一段 match_count 必须全部为 1 且 parent_ok 全部为 t；第二段 screens_count 必须为 1、parent_id 为空。
BEGIN READ ONLY;

WITH desired(title_key, parent_key) AS (
    VALUES
        ('sys.nav.portal.dataArchitecture', NULL),
        ('sys.nav.portal.dataIntegration', NULL),
        ('sys.nav.portal.dataStandards', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.dimensionalModeling', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.dataMetrics', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.modelingGraphs', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.modelingHomeWorkspace', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.modelingTools', 'sys.nav.portal.studioDataModeling'),
        ('sys.nav.portal.metricAtomic', 'sys.nav.portal.dataMetrics'),
        ('sys.nav.portal.metricComposite', 'sys.nav.portal.dataMetrics'),
        ('sys.nav.portal.governanceClassification', 'sys.nav.portal.governanceOperations'),
        ('sys.nav.portal.governanceQualityRules', 'sys.nav.portal.governanceOperations'),
        ('sys.nav.portal.governanceQualityReport', 'sys.nav.portal.governanceOperations'),
        ('sys.nav.portal.serviceCenter', 'sys.nav.portal.dataConsumption'),
        ('sys.nav.portal.businessIntelligenceApps', 'sys.nav.portal.dataConsumption')
),
matches AS (
    SELECT d.title_key, d.parent_key, m.id, m.sort_order, p.name AS parent_name,
           p.metadata::jsonb ->> 'titleKey' AS parent_title_key, m.parent_id
      FROM desired d
      LEFT JOIN portal_menu m
        ON m.deleted = FALSE
       AND (m.name = d.title_key OR m.metadata::jsonb ->> 'titleKey' = d.title_key)
      LEFT JOIN portal_menu p ON p.id = m.parent_id AND p.deleted = FALSE
)
SELECT title_key,
       COUNT(id) AS match_count,
       string_agg(id::TEXT || '@' || COALESCE(sort_order::TEXT, '-'), ',') AS ids_and_orders,
       bool_and(
           CASE
               WHEN parent_key IS NULL THEN parent_id IS NULL
               ELSE parent_key IN (parent_name, parent_title_key)
           END
       ) AS parent_ok
  FROM matches
 GROUP BY title_key
 ORDER BY title_key;

SELECT COUNT(*) AS screens_count,
       string_agg(id || ' parent=' || COALESCE(parent_id::TEXT, 'null') || ' sort=' || sort_order
                  || ' by=' || COALESCE(last_modified_by, '-'), '; ') AS screens_rows
  FROM portal_menu
 WHERE deleted = FALSE
   AND (name = 'sys.nav.portal.biScreens' OR metadata::jsonb ->> 'titleKey' = 'sys.nav.portal.biScreens');

SELECT cfg_value AS seed_hash, last_modified_by
  FROM system_config
 WHERE cfg_key IN ('portal.menu.seed.hash', 'portal.menu.order.snapshot.20260916-03');

ROLLBACK;
