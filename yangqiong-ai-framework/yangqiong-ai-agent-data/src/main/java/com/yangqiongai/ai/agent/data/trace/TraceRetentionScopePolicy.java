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
package com.yangqiongai.ai.agent.data.trace;

import java.util.Map;

/**
 * Trace保留期scope覆盖策略
 * <p>
 * 提供按scope维度的保留期覆盖（scopeId → 保留天数），供保留期清理任务
 * 对不同工作区执行差异化清理；无覆盖的scope沿用全局retention-days。
 * </p>
 * @author yangqiong
 */
public interface TraceRetentionScopePolicy {

    /**
     * 查询全部scope保留期覆盖
     * @return scopeId → 保留天数（空Map表示无覆盖）
     */
    Map<String, Integer> getScopeRetentionDays();
}
