package com.fongmi.android.tv.diagnostic;

import java.util.regex.Pattern;

public final class LogRedactor {

    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\\\"(?:authorization|proxy-authorization|cookie|set-cookie|access[_-]?token|token|api[_-]?key|apikey|password|passwd|pwd)\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")");
    private static final Pattern HEADER_SECRET = Pattern.compile("(?im)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*:\\s*[^\\r\\n]+");
    private static final Pattern AUTH_PARAMETER = Pattern.compile("(?i)(\\b(?:authorization|proxy-authorization)\\s*=\\s*)(?:bearer\\s+)?[^\\s,;&\\\"']+");
    private static final Pattern PARAMETER_SECRET = Pattern.compile("(?i)(\\b(?:cookie|access[_-]?token|token|api[_-]?key|apikey|password|passwd|pwd)\\s*[=:]\\s*)([^\\s,;&\\\"']+)");
    private static final Pattern USER_INFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s:]+:[^/@\\s]+@");

    private LogRedactor() {
    }

    public static String redact(String value) {
        String safe = String.valueOf(value);
        safe = JSON_SECRET.matcher(safe).replaceAll("$1***$3");
        safe = HEADER_SECRET.matcher(safe).replaceAll("$1: ***");
        safe = AUTH_PARAMETER.matcher(safe).replaceAll("$1***");
        safe = PARAMETER_SECRET.matcher(safe).replaceAll("$1***");
        return USER_INFO.matcher(safe).replaceAll("$1***:***@");
    }
}
