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
package com.yangqiongai.ai.agent.runtime.tool;

import java.util.Collections;
import java.util.Map;

/**
 * Agent工具调用参数
 * @author yangqiong
 */
public final class AgentToolCallParam {

    /**
     * 工具调用输入参数
     */
    private final Map<String, Object> input;

    public AgentToolCallParam(Map<String, Object> input) {
        this.input = input != null ? Map.copyOf(input) : Collections.emptyMap();
    }

    /**
     * 获取工具调用输入参数
     * @return
     */
    public Map<String, Object> getInput() {
        return input;
    }
}
