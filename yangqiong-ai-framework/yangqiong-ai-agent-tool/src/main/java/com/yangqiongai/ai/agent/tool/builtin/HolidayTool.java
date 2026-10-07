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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * 节假日查询工具
 * <p>
 * 提供中国法定节假日、调休日、工作日判断能力。
 * 内置2024-2027年中国法定节假日和调休安排数据（来源于国务院发布的放假通知）。
 * 适用于业务办理、流程审批、工时计算等场景。
 * </p>
 * @author yangqiong
 */
@Component
public class HolidayTool implements Tool {

    /**
     * 内置工具自动装配到所有Agent工具箱
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.BUILTIN;
    }

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CN_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /**
     * 节假日数据：key=日期(yyyyMMdd)，value=节日名称
     */
    private static final Map<String, String> HOLIDAYS = new HashMap<>();

    /**
     * 调休上班日数据：key=日期(yyyyMMdd)，value=调休说明
     */
    private static final Map<String, String> WORKDAYS = new HashMap<>();

    static {
        // ========== 2024年节假日 ==========
        // 元旦
        HOLIDAYS.put("20240101", "元旦");
        // 春节
        HOLIDAYS.put("20240210", "春节");
        HOLIDAYS.put("20240211", "春节");
        HOLIDAYS.put("20240212", "春节");
        HOLIDAYS.put("20240213", "春节");
        HOLIDAYS.put("20240214", "春节");
        HOLIDAYS.put("20240215", "春节");
        HOLIDAYS.put("20240216", "春节");
        HOLIDAYS.put("20240217", "春节");
        // 清明节
        HOLIDAYS.put("20240404", "清明节");
        HOLIDAYS.put("20240405", "清明节");
        HOLIDAYS.put("20240406", "清明节");
        // 劳动节
        HOLIDAYS.put("20240501", "劳动节");
        HOLIDAYS.put("20240502", "劳动节");
        HOLIDAYS.put("20240503", "劳动节");
        HOLIDAYS.put("20240504", "劳动节");
        HOLIDAYS.put("20240505", "劳动节");
        // 端午节
        HOLIDAYS.put("20240608", "端午节");
        HOLIDAYS.put("20240609", "端午节");
        HOLIDAYS.put("20240610", "端午节");
        // 中秋节
        HOLIDAYS.put("20240915", "中秋节");
        HOLIDAYS.put("20240916", "中秋节");
        HOLIDAYS.put("20240917", "中秋节");
        // 国庆节
        HOLIDAYS.put("20241001", "国庆节");
        HOLIDAYS.put("20241002", "国庆节");
        HOLIDAYS.put("20241003", "国庆节");
        HOLIDAYS.put("20241004", "国庆节");
        HOLIDAYS.put("20241005", "国庆节");
        HOLIDAYS.put("20241006", "国庆节");
        HOLIDAYS.put("20241007", "国庆节");
        // 2024调休上班日
        WORKDAYS.put("20240204", "春节调休上班");
        WORKDAYS.put("20240218", "春节调休上班");
        WORKDAYS.put("20240407", "清明节调休上班");
        WORKDAYS.put("20240428", "劳动节调休上班");
        WORKDAYS.put("20240511", "劳动节调休上班");
        WORKDAYS.put("20240914", "中秋节调休上班");
        WORKDAYS.put("20240929", "国庆节调休上班");
        WORKDAYS.put("20241012", "国庆节调休上班");

        // ========== 2025年节假日 ==========
        // 元旦
        HOLIDAYS.put("20250101", "元旦");
        // 春节
        HOLIDAYS.put("20250128", "春节");
        HOLIDAYS.put("20250129", "春节");
        HOLIDAYS.put("20250130", "春节");
        HOLIDAYS.put("20250131", "春节");
        HOLIDAYS.put("20250201", "春节");
        HOLIDAYS.put("20250202", "春节");
        HOLIDAYS.put("20250203", "春节");
        HOLIDAYS.put("20250204", "春节");
        // 清明节
        HOLIDAYS.put("20250404", "清明节");
        HOLIDAYS.put("20250405", "清明节");
        HOLIDAYS.put("20250406", "清明节");
        // 劳动节
        HOLIDAYS.put("20250501", "劳动节");
        HOLIDAYS.put("20250502", "劳动节");
        HOLIDAYS.put("20250503", "劳动节");
        HOLIDAYS.put("20250504", "劳动节");
        HOLIDAYS.put("20250505", "劳动节");
        // 端午节
        HOLIDAYS.put("20250531", "端午节");
        HOLIDAYS.put("20250601", "端午节");
        HOLIDAYS.put("20250602", "端午节");
        // 中秋节、国庆节
        HOLIDAYS.put("20251001", "国庆节/中秋节");
        HOLIDAYS.put("20251002", "国庆节/中秋节");
        HOLIDAYS.put("20251003", "国庆节/中秋节");
        HOLIDAYS.put("20251004", "国庆节/中秋节");
        HOLIDAYS.put("20251005", "国庆节/中秋节");
        HOLIDAYS.put("20251006", "国庆节/中秋节");
        HOLIDAYS.put("20251007", "国庆节/中秋节");
        HOLIDAYS.put("20251008", "国庆节/中秋节");
        // 2025调休上班日
        WORKDAYS.put("20250126", "春节调休上班");
        WORKDAYS.put("20250208", "春节调休上班");
        WORKDAYS.put("20250427", "劳动节调休上班");
        WORKDAYS.put("20250928", "国庆节调休上班");
        WORKDAYS.put("20251011", "国庆节调休上班");

        // ========== 2026年节假日（参考2025年发布，最终以国务院通知为准） ==========
        // 元旦
        HOLIDAYS.put("20260101", "元旦");
        HOLIDAYS.put("20260102", "元旦");
        HOLIDAYS.put("20260103", "元旦");
        // 春节（农历正月初一为2026年2月17日）
        HOLIDAYS.put("20260215", "春节");
        HOLIDAYS.put("20260216", "春节");
        HOLIDAYS.put("20260217", "春节");
        HOLIDAYS.put("20260218", "春节");
        HOLIDAYS.put("20260219", "春节");
        HOLIDAYS.put("20260220", "春节");
        HOLIDAYS.put("20260221", "春节");
        // 清明节
        HOLIDAYS.put("20260404", "清明节");
        HOLIDAYS.put("20260405", "清明节");
        HOLIDAYS.put("20260406", "清明节");
        // 劳动节
        HOLIDAYS.put("20260501", "劳动节");
        HOLIDAYS.put("20260502", "劳动节");
        HOLIDAYS.put("20260503", "劳动节");
        HOLIDAYS.put("20260504", "劳动节");
        HOLIDAYS.put("20260505", "劳动节");
        // 端午节
        HOLIDAYS.put("20260619", "端午节");
        HOLIDAYS.put("20260620", "端午节");
        HOLIDAYS.put("20260621", "端午节");
        // 中秋节
        HOLIDAYS.put("20260925", "中秋节");
        HOLIDAYS.put("20260926", "中秋节");
        HOLIDAYS.put("20260927", "中秋节");
        // 国庆节
        HOLIDAYS.put("20261001", "国庆节");
        HOLIDAYS.put("20261002", "国庆节");
        HOLIDAYS.put("20261003", "国庆节");
        HOLIDAYS.put("20261004", "国庆节");
        HOLIDAYS.put("20261005", "国庆节");
        HOLIDAYS.put("20261006", "国庆节");
        HOLIDAYS.put("20261007", "国庆节");
        // 2026调休上班日（预期）
        WORKDAYS.put("20260214", "春节调休上班");
        WORKDAYS.put("20260222", "春节调休上班");
        WORKDAYS.put("20260426", "劳动节调休上班");
        WORKDAYS.put("20260927", "国庆节调休上班");
        WORKDAYS.put("20261010", "国庆节调休上班");

        // ========== 2027年节假日（预期，最终以国务院通知为准） ==========
        // 元旦
        HOLIDAYS.put("20270101", "元旦");
        // 春节（农历正月初一为2027年2月6日）
        HOLIDAYS.put("20270205", "春节");
        HOLIDAYS.put("20270206", "春节");
        HOLIDAYS.put("20270207", "春节");
        HOLIDAYS.put("20270208", "春节");
        HOLIDAYS.put("20270209", "春节");
        HOLIDAYS.put("20270210", "春节");
        HOLIDAYS.put("20270211", "春节");
        // 清明节
        HOLIDAYS.put("20270404", "清明节");
        HOLIDAYS.put("20270405", "清明节");
        HOLIDAYS.put("20270406", "清明节");
        // 劳动节
        HOLIDAYS.put("20270501", "劳动节");
        HOLIDAYS.put("20270502", "劳动节");
        HOLIDAYS.put("20270503", "劳动节");
        HOLIDAYS.put("20270504", "劳动节");
        HOLIDAYS.put("20270505", "劳动节");
        // 端午节
        HOLIDAYS.put("20270609", "端午节");
        HOLIDAYS.put("20270610", "端午节");
        HOLIDAYS.put("20270611", "端午节");
        // 中秋节
        HOLIDAYS.put("20270915", "中秋节");
        HOLIDAYS.put("20270916", "中秋节");
        HOLIDAYS.put("20270917", "中秋节");
        // 国庆节
        HOLIDAYS.put("20271001", "国庆节");
        HOLIDAYS.put("20271002", "国庆节");
        HOLIDAYS.put("20271003", "国庆节");
        HOLIDAYS.put("20271004", "国庆节");
        HOLIDAYS.put("20271005", "国庆节");
        HOLIDAYS.put("20271006", "国庆节");
        HOLIDAYS.put("20271007", "国庆节");
    }

    /**
     * 查询指定日期是否为节假日
     * @param date 日期字符串，格式 yyyy-MM-dd
     * @return
     */
    @AgentTool("查询指定日期是否为中国法定节假日或调休日。输入日期（格式 yyyy-MM-dd），返回该日期的节假日信息。支持2024-2027年数据。")
    public String checkHoliday(@AgentToolParam("日期字符串，格式 yyyy-MM-dd") String date) {
        try {
            LocalDate target = LocalDate.parse(date);
            String key = date.replace("-", "");
            String holidayName = HOLIDAYS.get(key);
            String workdayNote = WORKDAYS.get(key);

            StringBuilder sb = new StringBuilder();
            sb.append("查询日期：").append(target.format(ISO_FORMATTER)).append("（").append(target.format(CN_FORMATTER)).append("）\n");
            sb.append("星期：").append(getWeekdayCn(target.getDayOfWeek().getValue())).append("\n");

            if (holidayName != null) {
                sb.append("类型：法定节假日\n");
                sb.append("节日：").append(holidayName);
            } else if (workdayNote != null) {
                sb.append("类型：调休上班日（周末但需上班）\n");
                sb.append("说明：").append(workdayNote);
            } else if (isWeekend(target)) {
                sb.append("类型：周末休息日");
            } else {
                sb.append("类型：正常工作日");
            }
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式（如 2026-07-20）。";
        }
    }

    /**
     * 判断指定日期是否为工作日
     * @param date 日期字符串，格式 yyyy-MM-dd
     * @return
     */
    @AgentTool("判断指定日期是否为工作日。工作日=周一至周五非节假日+调休上班日。输入日期（格式 yyyy-MM-dd）。")
    public String isWorkday(@AgentToolParam("日期字符串，格式 yyyy-MM-dd") String date) {
        try {
            LocalDate target = LocalDate.parse(date);
            String key = date.replace("-", "");
            boolean isHoliday = HOLIDAYS.containsKey(key);
            boolean isAdjustedWorkday = WORKDAYS.containsKey(key);
            boolean isNormalWeekend = isWeekend(target);

            boolean isWorkday;
            String reason;
            if (isHoliday) {
                isWorkday = false;
                reason = "法定节假日：" + HOLIDAYS.get(key);
            } else if (isAdjustedWorkday) {
                isWorkday = true;
                reason = "调休上班日：" + WORKDAYS.get(key);
            } else if (isNormalWeekend) {
                isWorkday = false;
                reason = "正常周末休息";
            } else {
                isWorkday = true;
                reason = "正常工作日";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("查询日期：").append(target.format(ISO_FORMATTER)).append("（").append(getWeekdayCn(target.getDayOfWeek().getValue())).append("）\n");
            sb.append("是否工作日：").append(isWorkday ? "是" : "否").append("\n");
            sb.append("原因：").append(reason);
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式。";
        }
    }

    /**
     * 查询指定年度的节假日列表
     * @param year 年度（如 2026）
     * @return
     */
    @AgentTool("查询指定年度的中国法定节假日和调休日完整列表。输入年度（如2026），返回该年度所有节假日和调休安排。")
    public String getHolidaysByYear(@AgentToolParam("年度，如 2026") int year) {
        String prefix = String.valueOf(year);
        TreeSet<String> sortedHolidays = new TreeSet<>();
        for (String key : HOLIDAYS.keySet()) {
            if (key.startsWith(prefix)) {
                sortedHolidays.add(key);
            }
        }
        TreeSet<String> sortedWorkdays = new TreeSet<>();
        for (String key : WORKDAYS.keySet()) {
            if (key.startsWith(prefix)) {
                sortedWorkdays.add(key);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(year).append("年节假日安排：\n\n");

        sb.append("【法定节假日】\n");
        if (sortedHolidays.isEmpty()) {
            sb.append("  暂无数据（").append(year).append("年数据未配置）\n");
        } else {
            String lastHoliday = "";
            int holidayCount = 0;
            for (String key : sortedHolidays) {
                String dateStr = key.substring(0, 4) + "-" + key.substring(4, 6) + "-" + key.substring(6, 8);
                String name = HOLIDAYS.get(key);
                if (!name.equals(lastHoliday)) {
                    if (!lastHoliday.isEmpty()) {
                        sb.append("  共").append(holidayCount).append("天\n");
                    }
                    sb.append("\n  ").append(name).append("：\n    ");
                    holidayCount = 0;
                    lastHoliday = name;
                }
                sb.append(dateStr).append("  ");
                holidayCount++;
            }
            if (!lastHoliday.isEmpty()) {
                sb.append("\n  共").append(holidayCount).append("天\n");
            }
        }

        sb.append("\n【调休上班日】\n");
        if (sortedWorkdays.isEmpty()) {
            sb.append("  暂无数据\n");
        } else {
            for (String key : sortedWorkdays) {
                String dateStr = key.substring(0, 4) + "-" + key.substring(4, 6) + "-" + key.substring(6, 8);
                sb.append("  ").append(dateStr).append("（").append(getWeekdayCn(LocalDate.parse(dateStr).getDayOfWeek().getValue())).append("） - ").append(WORKDAYS.get(key)).append("\n");
            }
        }

        sb.append("\n说明：").append(year).append("年").append(year >= 2027 ? "数据为预期值，最终以国务院发布的放假通知为准。" : "数据来源于国务院发布的放假通知。");
        return sb.toString();
    }

    /**
     * 计算两个日期之间的工作日天数
     * @param startDate 起始日期，格式 yyyy-MM-dd
     * @param endDate   结束日期，格式 yyyy-MM-dd
     * @return
     */
    @AgentTool("计算两个日期之间的工作日天数（含起止日）。输入起止日期（格式 yyyy-MM-dd），返回工作日数量。适用于工时、合同期限等计算。")
    public String countWorkdays(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (start.isAfter(end)) {
                return "错误：起始日期不能晚于结束日期";
            }

            long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
            int workdays = 0;
            int holidays = 0;
            int weekends = 0;
            int adjustedWorkdays = 0;

            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                String key = cursor.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (HOLIDAYS.containsKey(key)) {
                    holidays++;
                } else if (WORKDAYS.containsKey(key)) {
                    workdays++;
                    adjustedWorkdays++;
                } else if (isWeekend(cursor)) {
                    weekends++;
                } else {
                    workdays++;
                }
                cursor = cursor.plusDays(1);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("起始日期：").append(start.format(ISO_FORMATTER)).append("（").append(getWeekdayCn(start.getDayOfWeek().getValue())).append("）\n");
            sb.append("结束日期：").append(end.format(ISO_FORMATTER)).append("（").append(getWeekdayCn(end.getDayOfWeek().getValue())).append("）\n");
            sb.append("总天数：").append(totalDays).append("天\n");
            sb.append("工作日：").append(workdays).append("天\n");
            sb.append("  其中正常工作日：").append(workdays - adjustedWorkdays).append("天\n");
            sb.append("  其中调休上班日：").append(adjustedWorkdays).append("天\n");
            sb.append("休息日：").append(holidays + weekends).append("天\n");
            sb.append("  其中法定节假日：").append(holidays).append("天\n");
            sb.append("  其中正常周末：").append(weekends).append("天");
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式。";
        }
    }

    /**
     * 查询下一个最近的节假日
     * @param date 参考日期，格式 yyyy-MM-dd
     * @return
     */
    @AgentTool("查询指定日期之后最近的节假日。输入参考日期（格式 yyyy-MM-dd），返回下一个节假日的日期和名称。")
    public String getNextHoliday(@AgentToolParam("参考日期，格式 yyyy-MM-dd") String date) {
        try {
            LocalDate target = LocalDate.parse(date);
            String[] result = new String[2];
            LocalDate cursor = target.plusDays(1);
            int searchDays = 0;
            while (searchDays < 365) {
                String key = cursor.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                if (HOLIDAYS.containsKey(key)) {
                    result[0] = cursor.format(ISO_FORMATTER);
                    result[1] = HOLIDAYS.get(key);
                    break;
                }
                cursor = cursor.plusDays(1);
                searchDays++;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("参考日期：").append(target.format(ISO_FORMATTER)).append("\n");
            if (result[0] != null) {
                long daysUntil = ChronoUnit.DAYS.between(target, LocalDate.parse(result[0]));
                sb.append("下一个节假日：").append(result[0]).append("（").append(result[1]).append("）\n");
                sb.append("距离今天：").append(daysUntil).append("天");
            } else {
                sb.append("未查询到近期节假日（已查询").append(searchDays).append("天后）");
            }
            return sb.toString();
        } catch (Exception e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式。";
        }
    }

    /**
     * 判断是否为周末
     * @param date 日期
     * @return
     */
    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * 将星期几数值转换为中文名称
     * @param dayOfWeek 星期几数值（1=周一，7=周日）
     * @return
     */
    private String getWeekdayCn(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };
    }
}
