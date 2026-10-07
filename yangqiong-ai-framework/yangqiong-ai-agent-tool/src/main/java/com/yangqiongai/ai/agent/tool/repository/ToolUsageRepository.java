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
package com.yangqiongai.ai.agent.tool.repository;

import com.yangqiongai.ai.agent.core.provider.ToolUsageTracker;
import com.yangqiongai.ai.agent.tool.model.ToolUsageInfo;

import java.util.List;

/**
 * 工具使用量仓库
 * @author yangqiong
 */
public interface ToolUsageRepository extends ToolUsageTracker {

    /**
     * 获取工具使用量详情
     * @param toolCode
     * @return
     */
    ToolUsageInfo getByToolCode(String toolCode);

    /**
     * 获取使用量排行
     * @param limit
     * @return
     */
    List<ToolUsageInfo> listTopByCallCount(int limit);
}
