package com.yuzhi.dts.platform.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to normalize and align role strings used in allowRoles fields.
 *
 * Goals:
 * - Upper-case, trim, and ensure ROLE_ prefix for authorities
 * - Deduplicate and provide stable ordering
 * - Optionally align DEPT_ vs INST_ role families to a dataset scope
 */
public final class RoleUtils {

  private static final Pattern DATA_ROLE = Pattern.compile("ROLE_(DEPT|INST)_DATA_(VIEWER|DEV|OWNER)");

  private RoleUtils() {}

  /** Normalize a comma-separated roles CSV into Spring authorities array. */
  public static String[] toAuthorityArray(String rolesCsv) {
    if (rolesCsv == null || rolesCsv.isBlank()) return new String[0];
    return Arrays.stream(rolesCsv.split("\\s*,\\s*"))
        .filter(s -> s != null && !s.isBlank())
        .map(RoleUtils::normalizeSingle)
        .distinct()
        .toArray(String[]::new);
  }

  /** Normalize a CSV and return a normalized CSV joined by comma. */
  public static String normalizeCsv(String rolesCsv) {
    String[] arr = toAuthorityArray(rolesCsv);
    return String.join(",", arr);
  }

  /**
   * Normalize and align roles to given scope: if scope=DEPT, translate
   * ROLE_INST_DATA_* -> ROLE_DEPT_DATA_* and vice versa for INST.
   * Only recognized DATA roles are translated; other roles are left as-is.
   */
  public static String normalizeAndAlignToScope(String rolesCsv, String datasetScope) {
    if (rolesCsv == null || rolesCsv.isBlank()) return rolesCsv;
    String scope = datasetScope == null ? "" : datasetScope.trim().toUpperCase(Locale.ROOT);
    Set<String> out = new LinkedHashSet<>();
    for (String raw : rolesCsv.split("\\s*,\\s*")) {
      if (raw == null || raw.isBlank()) continue;
      String n = normalizeSingle(raw);
      String aligned = alignDataRoleToScope(n, scope);
      out.add(aligned);
    }
    return String.join(",", out);
  }

  private static String normalizeSingle(String role) {
    String v = role.trim().toUpperCase(Locale.ROOT);
    if (!v.startsWith("ROLE_")) v = "ROLE_" + v;
    return v;
  }

  private static String alignDataRoleToScope(String role, String scope) {
    if (!"DEPT".equals(scope) && !"INST".equals(scope)) return role;
    Matcher m = DATA_ROLE.matcher(role);
    if (!m.matches()) return role;
    String currentScope = m.group(1);
    String kind = m.group(2); // VIEWER|DEV|OWNER
    if (currentScope.equals(scope)) return role;
    // flip scope while keeping kind
    return "ROLE_" + scope + "_DATA_" + kind;
  }
}

