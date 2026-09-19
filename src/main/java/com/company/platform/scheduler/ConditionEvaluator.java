package com.company.platform.scheduler;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConditionEvaluator {
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "off");
    private static final Pattern STATUS = Pattern.compile("(?i)^status\\s*(==|!=)\\s*['\"]?([A-Z_]+)['\"]?$");

    private ConditionEvaluator() { }

    static boolean evaluate(String expression, String upstreamStatus) {
        String raw = expression == null ? "" : expression.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) return true;
        if (FALSE_VALUES.contains(normalized)) return false;
        Matcher matcher = STATUS.matcher(raw);
        if (matcher.matches()) {
            boolean equal = matcher.group(2).equalsIgnoreCase(upstreamStatus == null ? "" : upstreamStatus);
            return "==".equals(matcher.group(1)) ? equal : !equal;
        }
        throw new IllegalStateException("暂不支持的条件表达式：" + raw + "。当前支持 true/false 或 status == 'SUCCESS'");
    }
}
