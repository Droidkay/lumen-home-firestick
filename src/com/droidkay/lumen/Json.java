package com.droidkay.lumen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny, forgiving JSON reader. The inverter sometimes sends single quotes, Python-style None,
 * or several objects glued together, so this reads all of those.
 * Objects become Map, arrays become List, numbers become Double.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Every top-level {...} object found in the text, in order. */
    static List<Map<String, Object>> objects(String text) {
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (text == null) return out;
        int depth = 0, start = -1;
        boolean inStr = false;
        char quote = 0;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (inStr) {
                if (c == '\\') k++;
                else if (c == quote) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                quote = c;
            } else if (c == '{') {
                if (depth == 0) start = k;
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    try {
                        Object o = new Json(text.substring(start, k + 1)).value();
                        if (o instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) o;
                            out.add(m);
                        }
                    } catch (RuntimeException ignored) {
                        // skip a broken chunk, keep the rest
                    }
                    start = -1;
                }
            }
        }
        return out;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw new RuntimeException("eof");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"' || c == '\'') return string();
        if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) return number();
        String word = word();
        if (word.equals("true") || word.equals("True")) return Boolean.TRUE;
        if (word.equals("false") || word.equals("False")) return Boolean.FALSE;
        if (word.equals("null") || word.equals("None")) return null;
        return word;
    }

    private Map<String, Object> object() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (i < s.length()) {
            ws();
            String key;
            char c = s.charAt(i);
            key = (c == '"' || c == '\'') ? string() : word();
            ws();
            if (i < s.length() && s.charAt(i) == ':') i++;
            m.put(key, value());
            ws();
            if (i >= s.length()) break;
            c = s.charAt(i);
            i++;
            if (c == '}') break;
        }
        return m;
    }

    private List<Object> array() {
        ArrayList<Object> l = new ArrayList<Object>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return l;
        }
        while (i < s.length()) {
            l.add(value());
            ws();
            if (i >= s.length()) break;
            char c = s.charAt(i);
            i++;
            if (c == ']') break;
        }
        return l;
    }

    private String string() {
        char q = s.charAt(i++);
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == q) break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                if (e == 'n') b.append('\n');
                else if (e == 't') b.append('\t');
                else if (e == 'u' && i + 4 <= s.length()) {
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                } else b.append(e);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private Double number() {
        int st = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
            else break;
        }
        return Double.valueOf(s.substring(st, i));
    }

    private String word() {
        int st = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') i++;
            else break;
        }
        if (st == i) i++; // never get stuck
        return s.substring(st, i);
    }

    // ------------------------------------------------------------ helpers

    /** Walks a path like ("Batsoc", 0, 0). Returns null if anything is missing. */
    static Object at(Object root, Object... path) {
        Object cur = root;
        for (Object p : path) {
            if (cur instanceof Map && p instanceof String) cur = ((Map<?, ?>) cur).get(p);
            else if (cur instanceof List && p instanceof Integer) {
                List<?> l = (List<?>) cur;
                int idx = (Integer) p;
                cur = idx >= 0 && idx < l.size() ? l.get(idx) : null;
            } else return null;
            if (cur == null) return null;
        }
        return cur;
    }

    static double num(Object root, double scale, Object... path) {
        Object o = at(root, path);
        if (o instanceof Number) return ((Number) o).doubleValue() * scale;
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o) * scale;
            } catch (NumberFormatException ignored) {
            }
        }
        return Double.NaN;
    }

    static String str(Object root, Object... path) {
        Object o = at(root, path);
        if (o == null) return null;
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d)) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }
}
