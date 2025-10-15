package com.yuzhi.dts.platform.security;

import java.util.Arrays;
import java.util.Locale;

/**
 * Utilities to normalize and align role strings used in allowRoles fields.
 *
 * Goals:
 * - Upper-case, trim, and ensure ROLE_ prefix for authorities
 * - Deduplicate and provide stable ordering
 */
public final class RoleUtils {

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

  private static String normalizeSingle(String role) {
    String v = role.trim().toUpperCase(Locale.ROOT);
    if (!v.startsWith("ROLE_")) v = "ROLE_" + v;
    return v;
  }
}
