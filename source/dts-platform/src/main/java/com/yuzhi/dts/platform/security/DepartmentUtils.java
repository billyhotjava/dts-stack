package com.yuzhi.dts.platform.security;

public final class DepartmentUtils {

    private DepartmentUtils() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("[") && s.endsWith("]") && s.length() > 1) {
            s = s.substring(1, s.length() - 1);
        }
        int comma = s.indexOf(',');
        if (comma >= 0) {
            s = s.substring(0, comma);
        }
        s = s.replace("\"", "").trim().toUpperCase();
        if (s.isEmpty()) return "";
        s = s.replaceAll("[\\s_]+", "");
        if (s.startsWith("DEPT")) {
            s = s.substring(4);
        }
        if (s.length() > 1 && s.charAt(0) == 'D' && Character.isLetterOrDigit(s.charAt(1))) {
            s = s.substring(1);
        }
        while (s.startsWith("-")) {
            s = s.substring(1);
        }
        return s;
    }

    public static boolean matches(String ownerDept, String activeDept) {
        String owner = normalize(ownerDept);
        String context = normalize(activeDept);
        if (owner.isEmpty() || context.isEmpty()) {
            return false;
        }
        if (owner.equalsIgnoreCase(context)) {
            return true;
        }
        if (owner.endsWith(context) || context.endsWith(owner)) {
            return true;
        }
        if (ownerDept != null && activeDept != null) {
            return ownerDept.trim().equalsIgnoreCase(activeDept.trim());
        }
        return false;
    }
}
