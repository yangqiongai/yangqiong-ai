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
package com.yangqiongai.ai.open.capability.guard;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * 输出修复器
 * <p>
 * 调用LLM自动修复不合规的输出，使其符合JSON Schema定义。
 * </p>
 * @author yangqiong
 */
public class OutputRepairer {

    private static final Logger log = LoggerFactory.getLogger(OutputRepairer.class);

    private final AgentEngine agentEngine;

    @Prompt(code = "open.output.repair", name = "输出修复",
            description = "根据JSON Schema修复LLM输出使其符合结构要求", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String OUTPUT_REPAIR_PROMPT =
            "你是一个输出格式化助手。请根据以下JSON Schema规范修复输出内容，使其完全符合结构要求。\n\n"
            + "JSON Schema规范：\n${schemaContent}\n\n"
            + "原始输出：\n${output}\n\n"
            + "请只返回修复后的JSON格式内容，不要包含其他说明文字。";

    /**
     * 提示词解析器
     */
    @Autowired(required = false)
    private PromptResolver promptResolver;

    public OutputRepairer(AgentEngine agentEngine) {
        this.agentEngine = agentEngine;
    }

    /**
     * 修复不合规输出
     * @param output 原始输出
     * @param schemaPath Schema路径
     * @param context 修复上下文
     * @return 修复后的结构化输出
     */
    public Object repair(Object output, String schemaContent, RepairContext context) {
        String prompt = buildRepairPrompt(output, schemaContent, context);
        AgentRequest request = new AgentRequest()
                .agentCode("default")
                .input(prompt);
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            AgentResult result = agentEngine.run(request);
            success = result.isSuccess();
            if (result.isSuccess()) {
                String repairedText = result.getOutputAsText();
                // 尝试从修复结果中提取JSON
                return extractJson(repairedText);
            }
        } catch (Exception e) {
            log.warn("输出修复失败: capability={}", context.getCapabilityCode(), e);
        } finally {
            // 上报LLM执行结果
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("open.output.repair", context.getCapabilityCode(),
                        success, System.currentTimeMillis() - startTime);
            }
        }
        return output;
    }

    private String buildRepairPrompt(Object output, String schemaContent, RepairContext context) {
        String defaultContent = OUTPUT_REPAIR_PROMPT
                .replace("${schemaContent}", schemaContent)
                .replace("${output}", output == null ? "" : output.toString());
        if (promptResolver == null) {
            return defaultContent;
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("schemaContent", schemaContent);
        variables.put("output", output);
        return promptResolver.resolve("open.output.repair", defaultContent, variables);
    }

    private Object extractJson(String text) {
        if (text == null) {
            return null;
        }
        // 尝试提取JSON代码块
        int start = text.indexOf("```json");
        if (start >= 0) {
            start = start + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                text = text.substring(start, end).trim();
            }
        }
        // 尝试直接解析
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
        } catch (Exception e) {
            return text;
        }
    }
}