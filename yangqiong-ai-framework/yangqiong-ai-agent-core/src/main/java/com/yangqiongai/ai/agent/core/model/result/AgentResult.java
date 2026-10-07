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
package com.yangqiongai.ai.agent.core.model.result;

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;

import java.util.List;
import java.util.Map;

/**
 * Agent执行结果
 * @author yangqiong
 */
public class AgentResult {

    /**
     * 输出
     */
    private List<OutputBlock> output;

    /**
     * 最终载荷
     */
    private Map<String, Object> finalPayload;

    /**
     * 请求数据体
     */
    private Map<String, Object> body;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * Token消耗指标
     */
    private TokenMetrics tokenMetrics;

    /**
     * 是否暂停（等待审批）
     */
    private boolean paused;

    /**
     * 暂停时关联的审批请求ID
     */
    private String pausedRequestId;

    /**
     * 配额超限警示（WARN告警放行时由入口闸设置，同步路径随结果返回）
     */
    private String quotaWarning;

    /**
     * 成功结果（自定义输出块）
     * @param output
     * @return
     */
    public static AgentResult success(List<OutputBlock> output) {
        AgentResult r = new AgentResult();
        r.output = output;
        r.success = true;
        return r;
    }

    /**
     * 成功结果（纯文本输出，便捷重载，内部包装为TextOutputBlock）
     * @param text
     * @return
     */
    public static AgentResult success(String text) {
        return success(ContentBlockConverter.fromOutputText(text));
    }

    public static AgentResult failure(String errorMessage) {
        AgentResult r = new AgentResult();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    /**
     * 暂停结果（等待审批，不阻塞线程）
     * @param requestId
     * @param message
     * @return
     */
    public static AgentResult paused(String requestId, String message) {
        AgentResult r = new AgentResult();
        r.paused = true;
        r.pausedRequestId = requestId;
        r.output = ContentBlockConverter.fromOutputText(message);
        return r;
    }

    public AgentResult output(List<OutputBlock> output) {
        this.output = output;
        return this;
    }

    /**
     * 纯文本输出便捷重载，内部包装为TextOutputBlock
     * @param text
     * @return
     */
    public AgentResult output(String text) {
        this.output = ContentBlockConverter.fromOutputText(text);
        return this;
    }

    public AgentResult finalPayload(Map<String, Object> finalPayload) {
        this.finalPayload = finalPayload;
        return this;
    }

    public AgentResult body(Map<String, Object> body) {
        this.body = body;
        return this;
    }

    public AgentResult tokenMetrics(TokenMetrics tokenMetrics) {
        this.tokenMetrics = tokenMetrics;
        return this;
    }

    public List<OutputBlock> getOutput() {
        return output;
    }

    public void setOutput(List<OutputBlock> output) {
        this.output = output;
    }

    /**
     * 从AgentContentBlock列表设置输出
     * @param blocks
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setOutputFromBlocks(List<AgentContentBlock> blocks) {
        this.output = ContentBlockConverter.toOutputBlocks((List) blocks);
    }

    /**
     * 从多模态输出中抽取纯文本（拼接所有TextOutputBlock，以\n连接）
     * @return
     */
    public String getOutputAsText() {
        return ContentBlockConverter.toOutputText(output);
    }

    public Map<String, Object> getFinalPayload() {
        return finalPayload;
    }

    public void setFinalPayload(Map<String, Object> finalPayload) {
        this.finalPayload = finalPayload;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

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

    public TokenMetrics getTokenMetrics() {
        return tokenMetrics;
    }

    public void setTokenMetrics(TokenMetrics tokenMetrics) {
        this.tokenMetrics = tokenMetrics;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getPausedRequestId() {
        return pausedRequestId;
    }

    public void setPausedRequestId(String pausedRequestId) {
        this.pausedRequestId = pausedRequestId;
    }

    public String getQuotaWarning() {
        return quotaWarning;
    }

    public void setQuotaWarning(String quotaWarning) {
        this.quotaWarning = quotaWarning;
    }
}
