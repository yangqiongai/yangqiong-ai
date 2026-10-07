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

import java.util.List;

/**
 * Agent节点
 * @author yangqiong
 */
public class AgentNode extends WorkflowNode {

    /**
     * 获取Agent编码
     * @return
     */
    public String getAgentCode() {
        return getConfigString("agentCode");
    }

    /**
     * 设置Agent编码
     * @param agentCode
     */
    public void setAgentCode(String agentCode) {
        setConfigValue("agentCode", agentCode);
    }

    /**
     * 获取系统提示词
     * @return
     */
    public String getSysPrompt() {
        return getConfigString("sysPrompt");
    }

    /**
     * 设置系统提示词
     * @param sysPrompt
     */
    public void setSysPrompt(String sysPrompt) {
        setConfigValue("sysPrompt", sysPrompt);
    }

    /**
     * 获取最大迭代次数
     * @return
     */
    public Integer getMaxIterations() {
        return getConfigInt("maxIterations");
    }

    /**
     * 设置最大迭代次数
     * @param maxIterations
     */
    public void setMaxIterations(Integer maxIterations) {
        setConfigValue("maxIterations", maxIterations);
    }

    /**
     * 获取工具包引用列表
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<String> getToolkitRefs() {
        Object value = getConfigValue("toolkitRefs");
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    /**
     * 设置工具包引用列表
     * @param toolkitRefs
     */
    public void setToolkitRefs(List<String> toolkitRefs) {
        setConfigValue("toolkitRefs", toolkitRefs);
    }
}
