package com.company.platform.scheduler;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConditionEvaluator {
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "off");
    private static final Pattern COMPARISON = Pattern.compile("(?i)^([A-Z_][A-Z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)$");

    private ConditionEvaluator() { }

    static boolean evaluate(String expression, String upstreamStatus) {
        return evaluate(expression, upstreamStatus, Map.of());
    }

    static boolean evaluate(String expression, String upstreamStatus, Map<String,Object> variables) {
        String raw = expression == null ? "" : expression.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) return true;
        if (FALSE_VALUES.contains(normalized)) return false;
        Matcher matcher = COMPARISON.matcher(raw);
        if (!matcher.matches()) throw unsupported(raw);
        String key = matcher.group(1).toLowerCase(Locale.ROOT);
        String operator = matcher.group(2);
        Object left = "status".equals(key) ? upstreamStatus : lookup(variables, key);
        if (left == null) throw new IllegalStateException("条件变量不存在：" + matcher.group(1));
        Object right = literal(matcher.group(3));
        BigDecimal leftNumber = number(left), rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            int cmp = leftNumber.compareTo(rightNumber);
            return compare(cmp, operator);
        }
        String a = String.valueOf(left), b = String.valueOf(right);
        int cmp = a.compareToIgnoreCase(b);
        return compare(cmp, operator);
    }

    private static Object lookup(Map<String,Object> variables, String key) {
        if (variables == null) return null;
        for (Map.Entry<String,Object> entry : variables.entrySet())
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static Object literal(String raw) {
        String value = raw == null ? "" : raw.trim();
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\"")))
            return value.substring(1, value.length() - 1);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        try { return new BigDecimal(value); } catch (NumberFormatException ignored) { return value; }
    }

    private static BigDecimal number(Object value) {
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value instanceof String text) try { return new BigDecimal(text.trim()); } catch (NumberFormatException ignored) { }
        return null;
    }

    private static boolean compare(int cmp, String operator) {
        return switch (operator) {
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            default -> false;
        };
    }

    private static IllegalStateException unsupported(String expression) {
        return new IllegalStateException("暂不支持的条件表达式：" + expression + "。支持 status / row_count / SQL 首行标量字段与 == != > >= < <= 比较");
    }
}
