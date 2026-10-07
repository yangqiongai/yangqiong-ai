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
package com.yangqiongai.ai.workflow.model;

import lombok.Data;

import java.util.List;

/**
 * 节点时间控制配置
 * <p>
 * TIME_CONTROL节点的主配置，流程执行到该节点时暂停，到达设定时间后自动恢复并继续执行后续节点。
 * </p>
 *
 * @author yangqiong
 */
@Data
public class NodeTimeControlConfig {

    /**
     * 时间模式
     */
    private TimeType timeType = TimeType.DELAY;

    /**
     * 延迟秒数（DELAY模式）
     */
    private Integer delaySeconds;

    /**
     * 倒计时分钟数（COUNTDOWN模式）
     */
    private Integer countdownMinutes;

    /**
     * 倒计时秒数（COUNTDOWN模式）
     */
    private Integer countdownSeconds;

    /**
     * 具体时间（SPECIFIED模式，格式yyyy-MM-dd HH:mm:ss）
     */
    private String specificTime;

    /**
     * cron表达式（CRON模式，支持5段"分 时 日 月 周"与6段"秒 分 时 日 月 周"，如每15分钟：0 0/15 * * * *）
     */
    private String cronExpression;

    /**
     * 周期类型（PERIODIC模式）
     */
    private PeriodType periodType = PeriodType.DAILY;

    /**
     * 周期执行时间点（PERIODIC模式，格式HH:mm）
     */
    private String periodTime;

    /**
     * 周期执行日（WEEKLY模式，1=周一至7=周日）
     */
    private List<Integer> periodWeekdays;

    /**
     * 每月几号执行（MONTHLY模式，1-31，超出当月天数时顺延查找）
     */
    private Integer periodDayOfMonth;

    /**
     * 是否仅在工作日（周一至周五）执行，仅PERIODIC模式生效
     */
    private Boolean workdayOnly = false;

    /**
     * 时间模式
     */
    public enum TimeType {
        /**
         * 延迟指定秒数后继续
         */
        DELAY,

        /**
         * 倒计时（分+秒）后继续
         */
        COUNTDOWN,

        /**
         * 等待至具体时间点
         */
        SPECIFIC,

        /**
         * 等待至下一个周期时间点
         */
        PERIODIC,

        /**
         * 等待至cron表达式的下一次触发时间（支持每N分钟等高频间隔）
         */
        CRON
    }

    /**
     * 周期类型
     */
    public enum PeriodType {
        /**
         * 每天
         */
        DAILY,

        /**
         * 每周指定星期
         */
        WEEKLY,

        /**
         * 每月指定日期
         */
        MONTHLY
    }
}
