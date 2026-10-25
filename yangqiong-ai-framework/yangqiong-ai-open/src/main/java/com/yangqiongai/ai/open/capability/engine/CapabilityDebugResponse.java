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
package com.yangqiongai.ai.open.capability.engine;

import java.util.List;
import java.util.Map;

/**
 * 能力调试执行响应
 * <p>
 * 分步返回能力调用全流程的中间结果（入参校验 → Prompt渲染 → Agent执行 → 契约校验），
 * 供管理后台测试界面展示与问题定位。
 * </p>
 * @author yangqiong
 */
public class CapabilityDebugResponse {

    /**
     * 调试步骤结果
     */
    public static class DebugStep {

        /**
         * 步骤是否通过
         */
        private boolean passed;

        /**
         * 步骤说明（通过时的附加信息或跳过原因）
         */
        private String message;

        /**
         * 失败错误列表
         */
        private List<String> errors;

        public static DebugStep pass() {
            return pass(null);
        }

        public static DebugStep pass(String message) {
            DebugStep step = new DebugStep();
            step.passed = true;
            step.message = message;
            return step;
        }

        public static DebugStep fail(List<String> errors) {
            DebugStep step = new DebugStep();
            step.passed = false;
            step.errors = errors;
            return step;
        }

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }

    /**
     * 总体是否成功（全流程通过）
     */
    private boolean success;

    /**
     * 失败错误信息
     */
    private String errorMessage;

    /**
     * 调用ID（与调用追踪记录对齐）
     */
    private String callId;

    /**
     * 总耗时（毫秒）
     */
    private long durationMillis;

    /**
     * 步骤1：入参校验
     */
    private DebugStep inputValidation;

    /**
     * 步骤2：渲染后的Prompt（含Schema补充说明）
     */
    private String renderedPrompt;

    /**
     * 步骤3：Agent执行
     */
    private DebugStep agentExecution;

    /**
     * Agent原始输出文本
     */
    private String agentOutput;

    /**
     * 步骤4：输出契约校验
     */
    private DebugStep outputContract;

    /**
     * 解析后的结构化输出
     */
    private Object structuredOutput;

    /**
     * Token消耗
     */
    private Map<String, Object> tokenMetrics;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public DebugStep getInputValidation() {
        return inputValidation;
    }

    public void setInputValidation(DebugStep inputValidation) {
        this.inputValidation = inputValidation;
    }

    public String getRenderedPrompt() {
        return renderedPrompt;
    }

    public void setRenderedPrompt(String renderedPrompt) {
        this.renderedPrompt = renderedPrompt;
    }

    public DebugStep getAgentExecution() {
        return agentExecution;
    }

    public void setAgentExecution(DebugStep agentExecution) {
        this.agentExecution = agentExecution;
    }

    public String getAgentOutput() {
        return agentOutput;
    }

    public void setAgentOutput(String agentOutput) {
        this.agentOutput = agentOutput;
    }

    public DebugStep getOutputContract() {
        return outputContract;
    }

    public void setOutputContract(DebugStep outputContract) {
        this.outputContract = outputContract;
    }

    public Object getStructuredOutput() {
        return structuredOutput;
    }

    public void setStructuredOutput(Object structuredOutput) {
        this.structuredOutput = structuredOutput;
    }

    public Map<String, Object> getTokenMetrics() {
        return tokenMetrics;
    }

    public void setTokenMetrics(Map<String, Object> tokenMetrics) {
        this.tokenMetrics = tokenMetrics;
    }
}
