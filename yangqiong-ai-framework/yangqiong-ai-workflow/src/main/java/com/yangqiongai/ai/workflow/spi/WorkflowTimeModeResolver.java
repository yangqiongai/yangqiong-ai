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
package com.yangqiongai.ai.workflow.spi;

import com.yangqiongai.ai.workflow.model.NodeTimeControlConfig;

/**
 * 工作流时间模式解析
 * <p>
 * 延迟执行(DELAY)为社区内置模式，倒计时/具体时间/周期性时间/Cron表达式
 * 为企业版专属模式，由企业版提供本实现完成恢复时间计算。
 * 未注入实现时社区引擎仅支持延迟执行模式。
 * </p>
 * @author yangqiong
 */
public interface WorkflowTimeModeResolver {

    /**
     * 根据时间模式计算恢复时间点（毫秒时间戳）
     * @param config 时间控制配置（timeType非DELAY）
     * @param nowMillis 当前毫秒时间戳
     * @return 恢复时间点毫秒时间戳，配置无效返回-1
     */
    long resolveResumeAtMillis(NodeTimeControlConfig config, long nowMillis);
}
