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
package com.yangqiongai.ai.memory.agentmemory.model;

import java.util.List;

/**
 * Agent运行记忆注入结果
 * @author yangqiong
 */
public class AgentMemoryInjection {

    /**
     * 注入文本块（已按Token预算截断，直接拼入系统提示词）
     */
    private String text;

    /**
     * 预估Token数
     */
    private int tokenEstimate;

    /**
     * 命中记忆条目
     */
    private List<AgentMemoryEntryInfo> entries;

    public static AgentMemoryInjection empty() {
        AgentMemoryInjection injection = new AgentMemoryInjection();
        injection.setText("");
        injection.setTokenEstimate(0);
        injection.setEntries(List.of());
        return injection;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public void setTokenEstimate(int tokenEstimate) {
        this.tokenEstimate = tokenEstimate;
    }

    public List<AgentMemoryEntryInfo> getEntries() {
        return entries;
    }

    public void setEntries(List<AgentMemoryEntryInfo> entries) {
        this.entries = entries;
    }
}
