package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;

final class CsvUtils {

    private CsvUtils() {}

    static String stripBom(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (c == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString());
        return out;
    }
}
