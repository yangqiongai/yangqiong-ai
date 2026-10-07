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
package com.yangqiongai.ai.agent.core.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yangqiongai.ai.agent.runtime.config.AgentToolChoice;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillFilter;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillFilterMode;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.provider.DeclaredPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent请求
 * @author yangqiong
 */
public class AgentRequest {

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户输入（多模态内容块列表），兼容纯文本字符串与内容块数组两种JSON形式
     */
    @JsonProperty("input")
    @JsonDeserialize(using = InputBlocksDeserializer.class)
    private List<InputBlock> input;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 请求数据体
     */
    private Map<String, Object> body = new HashMap<>();

    /**
     * 请求级RAG重排序开关，null表示跟随Agent配置与全局配置
     */
    private Boolean rerankEnabled;

    public AgentRequest agentCode(String agentCode) {
        this.agentCode = agentCode;
        return this;
    }

    public AgentRequest sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * 多模态输入设置
     * @param input
     * @return
     */
    public AgentRequest input(List<InputBlock> input) {
        this.input = input;
        return this;
    }

    /**
     * 纯文本输入便捷重载，内部包装为 TextBlock
     * @param text
     * @return
     */
    public AgentRequest input(String text) {
        this.input = ContentBlockConverter.fromInputText(text);
        return this;
    }

    public AgentRequest userId(String userId) {
        this.userId = userId;
        return this;
    }

    public AgentRequest scopeId(String scopeId) {
        this.scopeId = scopeId;
        return this;
    }

    public AgentRequest body(Map<String, Object> body) {
        this.body = body;
        return this;
    }

    public AgentRequest addBody(String key, Object value) {
        if (this.body == null) {
            this.body = new HashMap<>();
        }
        this.body.put(key, value);
        return this;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }


    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<InputBlock> getInput() {
        return input;
    }

    /**
     * 设置多模态输入
     * @param input
     */
    public void setInput(List<InputBlock> input) {
        this.input = input;
    }

    /**
     * 纯文本输入便捷重载，内部包装为 TextBlock（仅供Java调用，不参与JSON映射）
     * @param text
     */
    @JsonIgnore
    public void setInput(String text) {
        this.input = ContentBlockConverter.fromInputText(text);
    }

    /**
     * 从多模态输入中抽取纯文本（拼接所有 TextBlock，以 \n 连接）
     * @return
     */
    @JsonIgnore
    public String getInputAsText() {
        return ContentBlockConverter.toInputText(input);
    }

    /**
     * 从AgentContentBlock列表设置输入
     * @param blocks
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setInputFromBlocks(List<AgentContentBlock> blocks) {
        this.input = ContentBlockConverter.toInputBlocks((List) blocks);
    }

    /**
     * 转换为AgentContentBlock列表
     * @return
     */
    @JsonIgnore
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AgentContentBlock> getInputAsBlocks() {
        return (List) ContentBlockConverter.fromInputBlocks(input);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    /**
     * 请求级RAG重排序开关链式设置
     * @param rerankEnabled
     * @return
     */
    public AgentRequest rerankEnabled(Boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
        return this;
    }

    public Boolean getRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(Boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    /**
     * 获取模型编码，优先取请求体中的modelCode
     * @return
     */
    public String getModelCode() {
        Object code = body.get(BodyKeys.MODEL_CODE);
        return code instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取自定义系统提示词
     * @return
     */
    public String getCustomSystemPrompt() {
        Object prompt = body.get(BodyKeys.SYSTEM_PROMPT);
        return prompt instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取编排模式（sequential/parallel/delegate）
     * @return
     */
    public String getOrchestrationMode() {
        Object mode = body.get(BodyKeys.ORCHESTRATION_MODE);
        return mode instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取子代理声明原始列表，由调用方进一步解析为SubagentDeclaration
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSubagentDeclarations() {
        Object subagents = body.get(BodyKeys.SUBAGENTS);
        if (subagents instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 获取会话配置
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSessionConfig() {
        Object config = body.get(BodyKeys.SESSION_CONFIG);
        return config instanceof Map<?, ?> map ? (Map<String, Object>) config : null;
    }

    /**
     * 获取知识上下文（由RAG切面注入）
     * @return
     */
    public String getKnowledgeContext() {
        Object ctx = body.get(BodyKeys.KNOWLEDGE_CONTEXT);
        return ctx instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取工具选择策略
     * @return
     */
    public AgentToolChoice getToolChoice() {
        Object choice = body.get(BodyKeys.TOOL_CHOICE);
        if (choice instanceof String s && !s.isBlank()) {
            try {
                return AgentToolChoice.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取权限模式（DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK）
     * @return
     */
    public String getPermissionMode() {
        Object mode = body.get(BodyKeys.PERMISSION_MODE);
        return mode instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取是否启用推理模式
     * 当前实现中 reasoningEnabled: false 和 不设置 reasoningEnabled 的效果是一样的——都是不设置 reasoningEffort ，
     * 使用模型默认行为。对于 deepseek-v4-flash 这类默认不 thinking 的模型，效果是正确的。但如果将来使用 deepseek-r1 
     * 等默认启用 thinking 的模型， reasoningEnabled: false 无法强制关闭 thinking（因为 SDK 的 GenerateOptions 
     * 没有显式的"关闭推理"字段，只有 reasoningEffort 来控制推理强度）
     * @return
     */
    public Boolean getReasoningEnabled() {
        Object enabled = body.get(BodyKeys.REASONING_ENABLED);
        return enabled instanceof Boolean b ? b : null;
    }

    /**
     * 获取推理努力级别
     * @return
     */
    public String getReasoningEffort() {
        Object effort = body.get(BodyKeys.REASONING_EFFORT);
        return effort instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 是否启用SDK内置子代理功能
     * <p>
     * 默认禁用。启用后LLM可通过agent_spawn等工具动态创建子代理，
     * 但子代理执行不经过企业级安全护栏/熔断/重试/任务记录。
     * </p>
     * @return
     */
    public boolean isSdkSubagentsEnabled() {
        Object enabled = body.get(BodyKeys.ENABLE_SDK_SUBAGENTS);
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 获取请求级技能过滤配置
     * <p>
     * 返回值为 Map，包含 mode（all/none/only/except/enable/disable）和 skills（List）字段。
     * 由 AbstractAgentProcessor.resolveSkillFilter 解析为 SkillFilter。
     * </p>
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSkillFilterConfig() {
        Object config = body.get(BodyKeys.SKILL_FILTER);
        return config instanceof Map<?, ?> map ? (Map<String, Object>) config : null;
    }

    /**
     * 获取请求级 SkillFilter，解析 body 中的 skillFilter 配置
     * @return
     */
    public AgentSkillFilter getSkillFilter() {
        Map<String, Object> config = getSkillFilterConfig();
        if (config == null || config.isEmpty()) {
            return null;
        }
        String modeStr = config.get("mode") instanceof String s ? s : "all";
        AgentSkillFilterMode mode;
        try {
            mode = AgentSkillFilterMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        Object skillsObj = config.get("skills");
        List<String> skills;
        if (skillsObj instanceof List<?> list) {
            skills = list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        } else {
            skills = List.of();
        }
        if (skills.isEmpty()) {
            if (mode == AgentSkillFilterMode.NONE) {
                return AgentSkillFilter.builder().mode(AgentSkillFilterMode.NONE).skills(List.of()).build();
            }
            return null;
        }
        return switch (mode) {
            case ONLY -> AgentSkillFilter.builder().mode(AgentSkillFilterMode.ONLY).skills(skills).build();
            case EXCEPT -> AgentSkillFilter.builder().mode(AgentSkillFilterMode.EXCEPT).skills(skills).build();
            case ENABLE -> AgentSkillFilter.builder().mode(AgentSkillFilterMode.ENABLE).skills(skills).build();
            case DISABLE -> AgentSkillFilter.builder().mode(AgentSkillFilterMode.DISABLE).skills(skills).build();
            default -> null;
        };
    }

    /**
     * 获取技能过滤叠加模式，true时base+runtime叠加，false时runtime覆盖base
     * @return
     */
    public boolean isSkillFilterOverlay() {
        Object value = body.get(BodyKeys.SKILL_FILTER_OVERLAY);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 获取规划模式开关，true表示进入Plan Mode（只规划不执行）
     * @return
     */
    public boolean isPlanModeEnabled() {
        Object value = body.get(BodyKeys.PLAN_MODE);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 获取工作区路径（本地模式处理器使用）
     * @return
     */
    public String getWorkspacePath() {
        Object path = body.get(BodyKeys.WORKSPACE_PATH);
        return path instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * 获取请求参数式声明的偏好列表
     * <p>
     * 从 body.declaredPreferences 读取结构化偏好，每项含 key/content 字段。
     * 示例：{"declaredPreferences":[{"key":"编程语言","content":"Python"}]}
     * </p>
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<DeclaredPreference> getDeclaredPreferences() {
        Object value = body.get(BodyKeys.DECLARED_PREFERENCES);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<DeclaredPreference> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String key = map.get("key") instanceof String s ? s : null;
                String content = map.get("content") instanceof String s ? s : null;
                if (content != null && !content.isBlank()) {
                    result.add(new DeclaredPreference(key, content, "REQUEST_BODY"));
                }
            }
        }
        return result;
    }

    /**
     * 获取种子上下文消息（轨迹分叉场景）
     * <p>
     * 从 body.seedMessages 读取，元素格式{role,content}。
     * body缺省或元素格式不符时返回空列表。
     * </p>
     * @return
     */
    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSeedMessages() {
        Object value = body.get(BodyKeys.SEED_MESSAGES);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    /**
     * AgentRequest body 键名常量，统一维护所有 body key
     */
    public static final class BodyKeys {

        private BodyKeys() {
        }

        /**
         * 模型编码
         */
        public static final String MODEL_CODE = "modelCode";

        /**
         * 自定义系统提示词
         */
        public static final String SYSTEM_PROMPT = "systemPrompt";

        /**
         * 编排模式
         */
        public static final String ORCHESTRATION_MODE = "orchestrationMode";

        /**
         * 子代理声明列表
         */
        public static final String SUBAGENTS = "subagents";

        /**
         * 会话配置
         */
        public static final String SESSION_CONFIG = "sessionConfig";

        /**
         * 知识上下文
         */
        public static final String KNOWLEDGE_CONTEXT = "knowledgeContext";

        /**
         * 知识命中证据列表（结构化检索证据，随结果finalPayload透出）
         */
        public static final String KNOWLEDGE_EVIDENCES = "knowledgeEvidences";

        /**
         * 工具选择策略（auto/required/none/specific）
         */
        public static final String TOOL_CHOICE = "toolChoice";

        /**
         * 权限模式（DEFAULT/ACCEPT_EDITS/EXPLORE/BYPASS/DONT_ASK）
         */
        public static final String PERMISSION_MODE = "permissionMode";

        /**
         * 技能过滤配置（含mode和skills字段）
         */
        public static final String SKILL_FILTER = "skillFilter";

        /**
         * 技能过滤叠加模式（true时base+runtime叠加，false时runtime覆盖base）
         */
        public static final String SKILL_FILTER_OVERLAY = "skillFilterOverlay";

        /**
         * 规划模式开关（true进入Plan Mode，只规划不执行）
         */
        public static final String PLAN_MODE = "planMode";

        /**
         * 任务ID（异步任务追踪）
         */
        public static final String TASK_ID = "taskId";

        /**
         * 父任务ID（子代理场景关联父任务）
         */
        public static final String PARENT_TASK_ID = "parentTaskId";

        /**
         * 分叉点：来源任务的模型调用序号（轨迹分叉场景）
         */
        public static final String FORK_CALL_SEQ = "forkCallSeq";

        /**
         * 代理调用路径（如 main>research>sql）
         */
        public static final String AGENT_PATH = "agentPath";

        /**
         * 是否自动生成子代理
         */
        public static final String AUTO_GENERATE = "autoGenerate";

        /**
         * 是否需要确认后再执行
         */
        public static final String REQUIRE_CONFIRMATION = "requireConfirmation";

        /**
         * 自动生成子代理的最大数量
         */
        public static final String MAX_SUBAGENTS = "maxSubagents";

        /**
         * 生成子代理规格使用的模型编码
         */
        public static final String GENERATION_MODEL_CODE = "generationModelCode";

        /**
         * 编排方案预览（内部使用）
         */
        public static final String ORCHESTRATION_PREVIEW = "_orchestrationPreview";

        /**
         * 是否启用推理模式
         */
        public static final String REASONING_ENABLED = "reasoningEnabled";

        /**
         * 推理努力级别（low/medium/high）
         */
        public static final String REASONING_EFFORT = "reasoningEffort";

        /**
         * 是否启用SDK内置子代理功能（默认禁用）
         * <p>
         * 启用后LLM可通过agent_spawn等工具动态创建子代理，但子代理执行不经过企业级安全/熔断/重试/任务记录。
         * 仅在有明确需求时开启。
         * </p>
         */
        public static final String ENABLE_SDK_SUBAGENTS = "enableSdkSubagents";

        /**
         * 工作区路径（本地模式处理器使用）
         */
        public static final String WORKSPACE_PATH = "workspacePath";

        /**
         * 请求参数式声明的偏好列表
         * <p>
         * 格式：[{"key":"编程语言","content":"Python"}, ...]
         * </p>
         */
        public static final String DECLARED_PREFERENCES = "declaredPreferences";

        /**
         * 种子上下文消息（轨迹分叉场景），元素{role,content}
         * <p>
         * 引擎在组装初始历史时并入种子消息，实现从指定模型调用的上下文继续执行。
         * </p>
         */
        public static final String SEED_MESSAGES = "seedMessages";
    }
}
