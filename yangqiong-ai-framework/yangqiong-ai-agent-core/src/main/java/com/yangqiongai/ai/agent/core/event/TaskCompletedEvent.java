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
package com.yangqiongai.ai.agent.core.event;

/**
 * 任务完成事件
 * <p>
 * Agent执行完成后发布，供下游模块（如自进化技能学习）监听。
 * </p>
 * <p>
 * 定位说明：本类为进程内 Spring 应用事件，不进入运行时 AgentEvent 事件流
 * （运行时事件见 com.yangqiongai.ai.agent.runtime.event）。
 * </p>
 *
 * @author yangqiong
 */
public class TaskCompletedEvent {

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 输入文本
     */
    private final String inputText;

    /**
     * 输出文本
     */
    private final String outputText;

    /**
     * 工具调用次数
     */
    private final int toolCallCount;

    /**
     * 是否成功
     */
    private final boolean success;

    public TaskCompletedEvent(String taskId, String userId, String agentCode,
                               String inputText, String outputText,
                               int toolCallCount, boolean success) {
        this.taskId = taskId;
        this.userId = userId;
        this.agentCode = agentCode;
        this.inputText = inputText;
        this.outputText = outputText;
        this.toolCallCount = toolCallCount;
        this.success = success;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getInputText() {
        return inputText;
    }

    public String getOutputText() {
        return outputText;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public boolean isSuccess() {
        return success;
    }
}
