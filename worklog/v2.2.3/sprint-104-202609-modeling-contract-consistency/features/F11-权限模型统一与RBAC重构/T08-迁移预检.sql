-- F11-T08 身份授权迁移只读预检（Sprint-104 F11）。
-- 用途：升级/切换前在目标库只读执行，逐项输出待处理数量；全部为 0 才允许进入回填。
-- 禁止事项：本文件只含 SELECT，不写、不删、不改；不要在业务高峰执行全表扫描项。
-- 82415d

-- P1 同名大小写仅差的 username（稳定键缺失时无法区分主体，必须逐项核验）
SELECT lower(username) AS uname, count(*), string_agg(DISTINCT username, ',' ) AS variants
  FROM admin_keycloak_user
 WHERE username IS NOT NULL
 GROUP BY lower(username) HAVING count(*) > 1;

-- P2 无 personCode 的存量账号（无法证明历史主体，回填时隔离）
SELECT id, username, keycloak_id
  FROM admin_keycloak_user
 WHERE person_code IS NULL OR btrim(person_code) = '';

-- P3 角色成员缺稳定键（双写/回填候选）
SELECT count(*) AS member_without_stable_key
  FROM admin_role_member
 WHERE keycloak_id IS NULL;

-- P4 授权绑定缺稳定键（双写/回填候选，范围随绑定保留）
SELECT count(*) AS assignment_without_stable_key
  FROM admin_role_assignment
 WHERE keycloak_id IS NULL;

-- P5 同名用户名的成员行（孤儿/继承风险；逐项核对授予时主体证据）
SELECT lower(username) AS uname, count(*)
  FROM admin_role_member
 GROUP BY lower(username) HAVING count(*) > 1;

-- P6 授权指向不存在目录用户的孤儿绑定
SELECT a.id, a.role, a.username
  FROM admin_role_assignment a
  LEFT JOIN admin_keycloak_user u
    ON lower(u.username) = lower(a.username)
 WHERE u.id IS NULL AND a.keycloak_id IS NULL
 LIMIT 100;

-- P7 带范围授权的分布（确认 scope_org_id/dataset_ids/operations 未丢失）
SELECT role,
       count(*) AS total,
       count(scope_org_id) AS with_org_scope,
       count(NULLIF(btrim(dataset_ids), '')) AS with_datasets,
       count(NULLIF(btrim(operations), '')) AS with_operations
  FROM admin_role_assignment
 GROUP BY role ORDER BY total DESC;

-- P8 待开户/待重试/冲突人员（切换前必须清零或有明确 owner）
SELECT sync_state, access_state, count(*)
  FROM admin_keycloak_user
 GROUP BY sync_state, access_state ORDER BY sync_state, access_state;
