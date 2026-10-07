/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.platform.connector.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL安全校验
 * <p>
 * 数据库连接器工具的安全封装：只读强制（拒绝DML/DDL与多语句）、
 * 表白名单校验、注释剥离后的关键字检测，防注入绕过。
 * </p>
 * @author yangqiong
 */
public final class SqlSafetyValidator {

    /**
     * 拒绝的写操作/管理操作关键字前缀（大小写不敏感，仅匹配语句首词与分号后首词）
     */
    private static final String[] FORBIDDEN_PREFIXES = {
            "INSERT", "UPDATE", "DELETE", "MERGE", "REPLACE", "TRUNCATE",
            "CREATE", "ALTER", "DROP", "RENAME", "GRANT", "REVOKE",
            "CALL", "EXECUTE", "EXEC", "SET", "USE", "LOCK", "UNLOCK",
            "HANDLER", "LOAD", "PREPARE", "DO", "PURGE", "OPTIMIZE", "REPAIR"
    };

    /**
     * 允许的只读语句首词
     */
    private static final String[] ALLOWED_PREFIXES = {
            "SELECT", "SHOW", "DESC", "DESCRIBE", "EXPLAIN", "WITH"
    };

    /**
     * FROM/JOIN后表名提取（含反引号/库名.表名形态）
     */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN)\\s+`?([A-Za-z0-9_$]+)`?(?:\\s*\\.\\s*`?([A-Za-z0-9_$]+)`?)?");

    /**
     * SELECT ... INTO OUTFILE/DUMPFILE服务端写文件检测
     */
    private static final Pattern INTO_FILE_PATTERN = Pattern.compile(
            "(?i)\\bINTO\\s+(?:OUTFILE|DUMPFILE)\\b");

    private SqlSafetyValidator() {
    }

    /**
     * 只读校验：剥离注释后检测语句首词与多语句
     * @param sql 原始SQL
     * @return 拒绝原因，通过时返回null
     */
    public static String checkReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL不能为空";
        }
        String stripped = stripComments(sql).trim();
        if (stripped.isEmpty()) {
            return "SQL不能为空";
        }
        // 多语句检测（字符串字面量内的分号不参与，先遮蔽字面量）
        String masked = maskLiterals(stripped);
        if (INTO_FILE_PATTERN.matcher(masked).find()) {
            return "禁止SELECT INTO OUTFILE/DUMPFILE服务端写文件";
        }
        String[] parts = masked.split(";", -1);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                return "禁止多语句执行";
            }
        }
        String first = firstWord(stripped);
        for (String forbidden : FORBIDDEN_PREFIXES) {
            if (first.equalsIgnoreCase(forbidden)) {
                return "只读连接禁止执行" + first + "操作";
            }
        }
        boolean allowed = false;
        for (String prefix : ALLOWED_PREFIXES) {
            if (first.equalsIgnoreCase(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            return "仅允许只读查询(SELECT/SHOW/DESC/EXPLAIN/WITH)";
        }
        return null;
    }

    /**
     * 表白名单校验（白名单为空表示不限制）
     * @param sql 原始SQL
     * @param whitelist 表白名单（表名，可含库名前缀），空或不传=不限制
     * @return 拒绝原因，通过时返回null
     */
    public static String checkWhitelist(String sql, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return null;
        }
        String stripped = stripComments(sql == null ? "" : sql);
        Matcher matcher = TABLE_PATTERN.matcher(stripped);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            String schema = matcher.group(1);
            String table = matcher.group(2);
            // schema.table形态取全名与纯表名两种口径
            tables.add(table != null ? schema + "." + table : schema);
            if (table != null) {
                tables.add(table);
            }
        }
        if (tables.isEmpty()) {
            return null;
        }
        for (String table : tables) {
            if (!matchesWhitelist(table, whitelist)) {
                return "表" + table + "不在白名单内";
            }
        }
        return null;
    }

    /**
     * 表名是否命中白名单（大小写不敏感，支持a.b全名或b表名命中）
     * @param table 表名（可能含库名前缀）
     * @param whitelist
     * @return
     */
    private static boolean matchesWhitelist(String table, List<String> whitelist) {
        String normalized = unquote(table).toLowerCase(Locale.ROOT);
        for (String item : whitelist) {
            String candidate = unquote(item == null ? "" : item).trim().toLowerCase(Locale.ROOT);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(normalized)
                    || (normalized.contains(".") && normalized.endsWith("." + candidate))
                    || (candidate.contains(".") && normalized.equals(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 表清单白名单校验（describe_tables用，白名单为空表示不限制）
     * @param tables 待校验表名
     * @param whitelist 表白名单（表名，可含库名前缀），空=不限制
     * @return 拒绝原因，通过时返回null
     */
    public static String checkTables(List<String> tables, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty() || tables == null || tables.isEmpty()) {
            return null;
        }
        for (String table : tables) {
            if (table == null || unquote(table).isBlank()) {
                continue;
            }
            if (!matchesWhitelist(table, whitelist)) {
                return "表" + table.trim() + "不在白名单内";
            }
        }
        return null;
    }

    /**
     * 提取剥离注释后的语句首词
     * @param sql 已剥离注释的SQL
     * @return
     */
    private static String firstWord(String sql) {
        String stripped = stripComments(sql).trim();
        Matcher matcher = Pattern.compile("^[`\\[\\\"]?([A-Za-z0-9_$]+)").matcher(stripped);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 剥离行注释(--与#)与块注释(/**\/)
     * @param sql
     * @return
     */
    static String stripComments(String sql) {
        if (sql == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(sql.length());
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (!inDouble && !inBacktick && c == '\'') {
                inSingle = !inSingle;
                result.append(c);
                continue;
            }
            if (!inSingle && !inBacktick && c == '"') {
                inDouble = !inDouble;
                result.append(c);
                continue;
            }
            if (!inSingle && !inDouble && c == '`') {
                inBacktick = !inBacktick;
                result.append(c);
                continue;
            }
            if (!inSingle && !inDouble && !inBacktick) {
                if (c == '-' && next == '-') {
                    while (i < sql.length() && sql.charAt(i) != '\n') {
                        i++;
                    }
                    result.append('\n');
                    continue;
                }
                if (c == '#') {
                    while (i < sql.length() && sql.charAt(i) != '\n') {
                        i++;
                    }
                    result.append('\n');
                    continue;
                }
                if (c == '/' && next == '*') {
                    i += 2;
                    while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++;
                    result.append(' ');
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    /**
     * 遮蔽字符串字面量内容（多语句检测用）
     * @param sql
     * @return
     */
    private static String maskLiterals(String sql) {
        char[] chars = sql.toCharArray();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inBacktick = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!inDouble && !inBacktick && c == '\'') {
                inSingle = !inSingle;
            } else if (!inSingle && !inBacktick && c == '"') {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble && c == '`') {
                inBacktick = !inBacktick;
            } else if ((inSingle && c != '\'') || (inDouble && c != '"')) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    /**
     * 去除引号包裹
     * @param identifier
     * @return
     */
    private static String unquote(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("`") && trimmed.endsWith("`"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
