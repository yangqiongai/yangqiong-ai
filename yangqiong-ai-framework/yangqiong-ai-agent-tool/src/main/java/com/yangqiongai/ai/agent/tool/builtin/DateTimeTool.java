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
package com.yangqiongai.ai.agent.tool.builtin;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 日期时间工具
 * <p>
 * 提供当前日期、时间、星期等信息的查询能力，供LLM在处理时间相关问题时自主调用。
 * 避免LLM依赖自身训练数据判断"今天"等相对时间，确保时间信息的准确性。
 * </p>
 * @author yangqiong
 */
@Component
public class DateTimeTool implements Tool {

    /**
     * 内置工具自动装配到所有Agent工具箱
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.BUILTIN;
    }

    /**
     * 中文日期格式化器（年月日 + 星期）
     */
    private static final DateTimeFormatter CN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);

    /**
     * 中文时间格式化器（时分秒）
     */
    private static final DateTimeFormatter CN_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH时mm分ss秒", Locale.CHINA);

    /**
     * 完整日期时间格式化器
     */
    private static final DateTimeFormatter FULL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH时mm分ss秒", Locale.CHINA);

    /**
     * ISO标准日期格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前完整日期时间
     * <p>
     * 返回当前年月日、星期、时分秒，以及ISO标准格式、Unix时间戳等信息。
     * 适用于需要完整时间上下文的场景，如"现在几点"、"今天日期"等。
     * </p>
     * @return
     */
    @AgentTool("获取当前完整日期时间信息，包括年月日、星期、时分秒、ISO标准格式和Unix时间戳。当用户询问\"今天\"、\"现在\"、\"当前时间\"等时间相关问题时调用此工具。")
    public String getCurrentDateTime() {
        LocalDateTime now = LocalDateTime.now();
        String cnFull = now.format(FULL_FORMATTER);
        String iso = now.format(ISO_FORMATTER);
        long epochSecond = now.toEpochSecond(ZoneOffset.ofHours(8));
        int dayOfYear = now.getDayOfYear();
        int weekOfYear = now.get(WeekFields.of(Locale.CHINA).weekOfYear());
        int quarter = (now.getMonthValue() - 1) / 3 + 1;

        StringBuilder sb = new StringBuilder();
        sb.append("当前日期时间：").append(cnFull).append("\n");
        sb.append("ISO格式：").append(iso).append("\n");
        sb.append("Unix时间戳（秒）：").append(epochSecond).append("\n");
        sb.append("星期几：").append(now.getDayOfWeek().getValue()).append("（").append(getWeekdayCn(now.getDayOfWeek().getValue())).append("）\n");
        sb.append("本年第几天：").append(dayOfYear).append("\n");
        sb.append("本年第几周：").append(weekOfYear).append("\n");
        sb.append("季度：第").append(quarter).append("季度\n");
        sb.append("时区：东八区（UTC+8，北京时间）");
        return sb.toString();
    }

    /**
     * 获取当前日期
     * <p>
     * 仅返回年月日和星期，适用于不需要精确时间的场景。
     * </p>
     * @return
     */
    @AgentTool("获取当前日期，返回年月日和星期几。当只需要日期不需要时间时调用此工具。")
    public String getCurrentDate() {
        LocalDate today = LocalDate.now();
        String cnDate = today.format(CN_DATE_FORMATTER);
        String isoDate = today.toString();
        int dayOfYear = today.getDayOfYear();
        int weekOfYear = today.get(WeekFields.of(Locale.CHINA).weekOfYear());
        int quarter = (today.getMonthValue() - 1) / 3 + 1;

        StringBuilder sb = new StringBuilder();
        sb.append("当前日期：").append(cnDate).append("\n");
        sb.append("ISO格式：").append(isoDate).append("\n");
        sb.append("星期几：").append(today.getDayOfWeek().getValue()).append("（").append(getWeekdayCn(today.getDayOfWeek().getValue())).append("）\n");
        sb.append("本年第几天：").append(dayOfYear).append("\n");
        sb.append("本年第几周：").append(weekOfYear).append("\n");
        sb.append("季度：第").append(quarter).append("季度");
        return sb.toString();
    }

    /**
     * 获取当前时间
     * <p>
     * 仅返回时分秒，适用于只需要时间不需要日期的场景。
     * </p>
     * @return
     */
    @AgentTool("获取当前时间，返回时分秒。当只需要时间不需要日期时调用此工具。")
    public String getCurrentTime() {
        LocalTime now = LocalTime.now();
        String cnTime = now.format(CN_TIME_FORMATTER);
        String isoTime = now.toString();
        int hour = now.getHour();

        String period;
        if (hour < 6) {
            period = "凌晨";
        } else if (hour < 9) {
            period = "早上";
        } else if (hour < 12) {
            period = "上午";
        } else if (hour < 14) {
            period = "中午";
        } else if (hour < 18) {
            period = "下午";
        } else if (hour < 22) {
            period = "晚上";
        } else {
            period = "深夜";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前时间：").append(cnTime).append("\n");
        sb.append("ISO格式：").append(isoTime).append("\n");
        sb.append("时间段：").append(period);
        return sb.toString();
    }

    /**
     * 获取指定日期是星期几
     * <p>
     * 根据用户输入的日期（格式：yyyy-MM-dd），返回该日期是星期几。
     * 适用于"2026年7月20日是星期几"这类问题。
     * </p>
     * @param date 日期字符串，格式为 yyyy-MM-dd（如 2026-07-20）
     * @return
     */
    @AgentTool("查询指定日期是星期几。输入日期格式为 yyyy-MM-dd（如 2026-07-20），返回该日期对应的星期几。")
    public String getWeekday(@AgentToolParam("日期字符串，格式 yyyy-MM-dd，如 2026-07-20") String date) {
        try {
            LocalDate targetDate = LocalDate.parse(date);
            int dayOfWeek = targetDate.getDayOfWeek().getValue();
            String weekdayCn = getWeekdayCn(dayOfWeek);
            String cnDate = targetDate.format(CN_DATE_FORMATTER);
            return "日期 " + date + " 是" + weekdayCn + "（星期" + dayOfWeek + "）\n完整表示：" + cnDate;
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式（如 2026-07-20）。错误信息：" + e.getMessage();
        }
    }

    /**
     * 计算两个日期之间的天数差
     * <p>
     * 计算从起始日期到结束日期之间的天数差。
     * 适用于"距离某天还有多少天"、"两个日期相差几天"等场景。
     * </p>
     * @param startDate 起始日期，格式为 yyyy-MM-dd
     * @param endDate   结束日期，格式为 yyyy-MM-dd
     * @return
     */
    @AgentTool("计算两个日期之间的天数差。输入起止日期（格式 yyyy-MM-dd），返回相差天数。适用于\"距离某天还有多少天\"等场景。")
    public String daysBetween(@AgentToolParam("起始日期，格式 yyyy-MM-dd") String startDate, @AgentToolParam("结束日期，格式 yyyy-MM-dd") String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            long days = ChronoUnit.DAYS.between(start, end);
            String startCn = start.format(CN_DATE_FORMATTER);
            String endCn = end.format(CN_DATE_FORMATTER);
            StringBuilder sb = new StringBuilder();
            sb.append("起始日期：").append(startCn).append("\n");
            sb.append("结束日期：").append(endCn).append("\n");
            if (days >= 0) {
                sb.append("相差：").append(days).append("天（结束日期晚于起始日期）");
            } else {
                sb.append("相差：").append(-days).append("天（结束日期早于起始日期）");
            }
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式（如 2026-07-20）。错误信息：" + e.getMessage();
        }
    }

    /**
     * 将星期几数值转换为中文名称
     * @param dayOfWeek 星期几数值（1=周一，7=周日）
     * @return
     */
    private String getWeekdayCn(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1: return "星期一";
            case 2: return "星期二";
            case 3: return "星期三";
            case 4: return "星期四";
            case 5: return "星期五";
            case 6: return "星期六";
            case 7: return "星期日";
            default: return "未知";
        }
    }
}
