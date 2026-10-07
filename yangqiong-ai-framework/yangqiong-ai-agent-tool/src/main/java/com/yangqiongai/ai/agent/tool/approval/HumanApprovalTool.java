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
package com.yangqiongai.ai.agent.tool.approval;

import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.ApprovalRequest;
import com.yangqiongai.ai.approval.ApprovalResponse;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import com.yangqiongai.ai.common.metrics.HumanTakeoverMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 人工审批工具（LLM自主决策）
 * <p>
 * 注册到Agent工具箱中，LLM在ReAct推理中可根据操作风险自主决定是否调用此工具
 * 请求人工审批。与 {@link com.yangqiongai.ai.approval.Suspendable} 注解的静态强制审批互补，
 * 此工具让Agent具备"感知风险并动态请求人工介入"的能力。
 * </p>
 *
 * <h3>LLM调用示例</h3>
 * <pre>
 * Thought: 用户要求删除所有用户数据，这是高风险操作，我应该先请求人工审批
 * Action: requestHumanApproval
 * Action Input: {
 *   "reason": "批量删除用户数据，涉及1234条记录，需要管理员确认",
 *   "options": "["确认删除","仅删除测试数据","取消"]",
 *   "inputFields": "["确认密码"]"
 * }
 * </pre>
 *
 * <h3>三种交互模式</h3>
 * <ul>
 *   <li><b>确认</b>：options和inputFields均为空，审批人只能通过/拒绝</li>
 *   <li><b>选择</b>：options非空，审批人从选项中选择一个</li>
 *   <li><b>表单</b>：inputFields非空，审批人填写表单字段</li>
 * </ul>
 *
 * @author yangqiong
 */
@Component
public class HumanApprovalTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HumanApprovalTool.class);

    @Autowired
    private ApprovalGate approvalGate;

    @Autowired
    private HumanTakeoverMetrics takeoverMetrics;

    /**
     * 请求人工审批
     * <p>
     * 当操作可能带来风险、涉及敏感数据、或需要人工确认时调用此工具。
     * 审批通过后可继续执行后续操作，审批拒绝或超时则应停止当前操作。
     * </p>
     *
     * @param reason 审批原因，说明为什么需要人工介入，应包含操作的具体风险和影响
     * @param options 供审批人选择的选项，JSON数组格式如["允许","拒绝"]，为空字符串时表示仅需确认
     * @param inputFields 需要审批人填写的字段名，JSON数组格式如["备注","优先级"]，为空字符串时不收集额外信息
     * @return 审批结果描述，包含状态和审批人的选择/输入
     */
    @AgentTool("当操作可能带来风险、涉及敏感数据、或需要人工确认时，调用此工具请求人工审批。审批通过后可继续执行操作。" +
            "参数options和inputFields为空字符串时表示仅需确认无需选择或填表。")
    public String requestHumanApproval(@AgentToolParam("审批原因，说明为什么需要人工介入，应包含操作的具体风险和影响") String reason, @AgentToolParam("供审批人选择的选项，JSON数组格式如[\"允许\",\"拒绝\"]，为空字符串时表示仅需确认") String options, @AgentToolParam("需要审批人填写的字段名，JSON数组格式如[\"备注\",\"优先级\"]，为空字符串时不收集额外信息") String inputFields) {
        String sessionId = SessionContext.getSessionId();
        String userId = SessionContext.getUserId();

        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("请求人工审批失败：未设置会话上下文");
            return "审批请求失败：未设置会话上下文，无法发起审批。请确保在Agent会话中调用。";
        }

        log.info("LLM发起人工审批请求: sessionId={}, userId={}, reason={}", sessionId, userId, reason);

        List<String> optionList = parseStringList(options);
        List<String> fieldList = parseStringList(inputFields);

        ApprovalRequest.Builder builder = ApprovalRequest.builder()
                .sessionId(sessionId)
                .userId(userId != null ? userId : "unknown")
                .resourceType("LLM_DYNAMIC")
                .targetName("humanApproval")
                .reason(reason != null ? reason : "LLM请求人工审批");

        if (!optionList.isEmpty()) {
            builder.options(optionList);
        }
        if (!fieldList.isEmpty()) {
            builder.inputFields(fieldList);
        }

        // 记录审批请求到接管率统计
        takeoverMetrics.recordApprovalRequest(sessionId, reason);

        ApprovalResponse response = approvalGate.requestApproval(builder.build());

        // 记录审批结果到接管率统计
        takeoverMetrics.recordApprovalResult(response.isApproved(), response.isRejected() == false && response.isApproved() == false);

        return formatResponse(response);
    }

    /**
     * 将审批结果格式化为LLM可理解的字符串
     * @param response
     * @return
     */
    private String formatResponse(ApprovalResponse response) {
        if (response.isApproved()) {
            StringBuilder sb = new StringBuilder("审批通过。");
            String selectedOption = response.getSelectedOption();
            if (selectedOption != null) {
                sb.append("审批人选择：").append(selectedOption).append("。");
            }
            Map<String, Object> fields = response.getFields();
            if (!fields.isEmpty()) {
                sb.append("审批人填写：").append(fields).append("。");
            }
            return sb.toString();
        } else if (response.isRejected()) {
            return "审批拒绝。原因：" + (response.getRejectReason() != null ? response.getRejectReason() : "未提供") + "。请停止当前操作或寻找替代方案。";
        } else {
            return "审批超时，未收到审批结果。请停止当前操作或稍后重试。";
        }
    }

    /**
     * 解析JSON数组字符串为List
     * @param json
     * @return
     */
    private List<String> parseStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return AiJsonUtils.fromJsonToList(json, String.class);
        } catch (Exception e) {
            log.warn("解析JSON数组失败，返回空列表: input={}", json, e);
            return Collections.emptyList();
        }
    }
}
