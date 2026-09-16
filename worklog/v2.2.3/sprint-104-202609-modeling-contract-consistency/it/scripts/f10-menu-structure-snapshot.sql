-- Sprint-104 F10 迁移前后结构快照（只读）。迁移前后各导出一次并 diff，结果必须完全一致。
-- 管理端菜单可见性只取决于父子关系、删除标记、元数据、密级与角色绑定，不取决于排序号，
-- 因此本快照一致即证明各角色可见页面集合不变（K96）。
-- 用法：psql -d dts_admin -At -v ON_ERROR_STOP=1 -f f10-menu-structure-snapshot.sql > before.txt
BEGIN READ ONLY;

SELECT 'menu|' || concat_ws('|', id, name, path, component, metadata, parent_id, deleted, icon, security_level)
  FROM portal_menu
 ORDER BY id;

SELECT 'binding|' || row_to_json(v)::TEXT
  FROM portal_menu_visibility v
 ORDER BY v.id;

ROLLBACK;
