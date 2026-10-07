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
package com.yangqiongai.ai.agent.core.bootstrap;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.LongTermMemoryToolRegistrar;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.AdvancedAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.agent.runtime.prompt.SystemPromptSections;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.llm.DefaultLlmModelService;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Agent初始化
 * @author yangqiong
 */
@Service
public class AgentBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AgentBootstrapService.class);

    private static final String USE_GUIDANCE = """

             # 启动指引

             - 涉及实时数据、统计数字、文档内容等模型记忆不可靠的信息时，直接使用工具检索后回答，无需征询用户同意。
             - 检索查询要具体，包含关键约束，避免过宽；首次结果不佳时换更精确的关键词重试一次，而非基于薄弱证据作答。
             - 最终回答基于工具证据；证据与问题无关或相互矛盾时，如实说明局限，不硬凑结论。
             - 关键前提不明确时，主动向用户提问澄清，不靠猜测推进。
             """;

    private final LanguageModelFactory languageModelFactory;

    private final DefaultLlmModelService defaultLlmModelService;

    /**
     * 长期记忆工具注册器（可选依赖，ai-memory 模块未启用时为 null）
     */
    @Autowired(required = false)
    private LongTermMemoryToolRegistrar longTermMemoryToolRegistrar;

    /**
     * Agent模型工厂，由适配器模块提供实现（如AgentScopeModelFactory）
     */
    @Autowired(required = false)
    private AgentModelFactory agentModelFactory;

    public AgentBootstrapService(LanguageModelFactory languageModelFactory, DefaultLlmModelService defaultLlmModelService) {
        this.languageModelFactory = languageModelFactory;
        this.defaultLlmModelService = defaultLlmModelService;
    }

    /**
     * 启动Agent上下文
     * @param data
     * @return
     */
    public AgentContext bootstrap(AgentBootstrap data) {
        validate(data);

        AgentRequest request = data.getRequest();
        AgentContext context = new AgentContext(request);

        AdvancedAgentRuntimeBuilder builder = data.getAgentBuilder();
        enrichSystemPrompt(builder, data);
        configureModel(builder, data);
        data.setAgentContext(context);
        configureToolchain(builder, data);
        configureMemoryAndIters(builder, data);
        // AgentRuntimeBuilder未暴露toolExecutionContext方法，工具执行上下文由运行时内部管理

        context.setAttribute(AgentContext.CTX_EXECUTION_AGENT, data.getAgentBuilder());
        context.setAttribute(AgentContext.CTX_INPUTS, data.getInputMessages());
        context.setAttribute(AgentContext.CTX_RESULT_CONVERTER, data.getResultConverter());
        if (data.getStructuredOutputType() != null) {
            context.setStructuredOutputType(data.getStructuredOutputType());
        }
        if (data.getSessionConfig() != null) {
            data.getSessionConfig().forEach(context::setAttribute);
        }
        if (data.getExtraAttributes() != null) {
            data.getExtraAttributes().forEach(context::setAttribute);
        }

        log.info("Agent初始化完成: agentCode={}, toolkitEnabled={}",
                Optional.ofNullable(request).map(AgentRequest::getAgentCode).orElse(""),
                data.isToolkitEnabled());
        return context;
    }

    /**
     * 校验
     * @param data
     */
    private void validate(AgentBootstrap data) {
        if (data == null) {
            throw new IllegalArgumentException("组装数据不能为空");
        }
        if (data.getAgentBuilder() == null) {
            throw new IllegalArgumentException("组装数据中缺少Agent构建器");
        }
    }

    /**
     * 丰富系统提示词，有工具时追加使用指引
     * @param builder
     * @param data
     */
    private void enrichSystemPrompt(AgentRuntimeBuilder builder, AgentBootstrap data) {
        String basePrompt = blankToEmpty(data.getSystemPrompt());
        if (!data.isToolkitEnabled() || basePrompt.contains("启动指引")) {
            builder.systemPrompt(basePrompt);
            return;
        }
        String enriched = basePrompt.isBlank() ? USE_GUIDANCE.trim() : basePrompt + USE_GUIDANCE;
        builder.systemPrompt(enriched);
        log.debug("已追加工具使用指引到系统提示词");
    }

    /**
     * 解析模型编码，优先取请求体中的modelCode，其次按agentCode解析默认模型
     * @param request
     * @return
     */
    public String resolveModelCode(AgentRequest request) {
        if (request == null) {
            return null;
        }
        String modelCode = request.getModelCode();
        if (modelCode == null) {
            modelCode = defaultLlmModelService.resolveModel(request.getAgentCode(), request.getUserId());
        }
        return modelCode;
    }

    /**
     * 配置模型
     * @param builder
     * @param data
     */
    private void configureModel(AgentRuntimeBuilder builder, AgentBootstrap data) {
        AgentRequest request = data.getRequest();
        String modelCode = resolveModelCode(request);
        if (modelCode != null && !modelCode.isBlank()) {
            if (agentModelFactory == null) {
                log.warn("AgentModelFactory未注入，无法配置模型，请确保适配器模块（如yangqiong-ai-agent-scope）已启用");
                return;
            }
            // 携带模型编码构建，调用级用量记录才能归属真实模型
            if (builder instanceof com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder harnessBuilder) {
                harnessBuilder.model(agentModelFactory.getModel(modelCode, null), modelCode);
            } else {
                builder.model(agentModelFactory.getModel(modelCode, null));
            }
            log.debug("已配置模型: modelCode={}", modelCode);
        } else {
            log.warn("未指定modelCode且无法解析默认模型，Agent执行可能失败");
        }
    }

    /**
     * 配置工具链，无工具时走降级路径
     * @param builder
     * @param data
     */
    @SuppressWarnings("deprecation")
    private void configureToolchain(AdvancedAgentRuntimeBuilder builder, AgentBootstrap data) {
        if (!data.isToolkitEnabled()) {
            log.debug("无工具箱配置，跳过工具链装配");
            return;
        }

        AgentToolkit toolkit = data.getToolkit();
        builder.toolkit(toolkit);

        // 动态重建模式优先级最高：通过HarnessSkillMiddleware每次call()动态重建技能箱
        if (data.isSkillDynamicRebuild() && data.getSkillMiddleware() != null) {
            builder.middleware(data.getSkillMiddleware());
            // 同步传入技能箱，引擎据此注册load_skill等技能工具，与提示词中的技能摘要配套
            if (data.getSkillBox() != null) {
                builder.skillBox(data.getSkillBox());
            }
            log.debug("已注册HarnessSkillMiddleware，技能箱将在每次call()时动态重建");
        } else {
            AgentSkillBox skillBox = data.getSkillBox();
            if (skillBox != null) {
                if (data.isSkillHookInject()) {
                    // Hook注入模式：SkillBox存入AgentContext，由中间件onSystemPrompt动态注入
                    // 此模式技能提示词不存入Memory，节省token
                    data.getAgentContext().setAttribute(AgentContext.CTX_SKILL_BOX, skillBox);
                    log.debug("技能箱已存入AgentContext，将由中间件动态注入");
                } else if (data.isSkillLoadOnDemand()) {
                    // 按需加载模式：AgentSkillBox暂不支持bindToolkit/registerSkillLoadTool，直接注入摘要
                    String skillPrompt = skillBox.buildSystemPrompt();
                    if (skillPrompt != null && !skillPrompt.isBlank()) {
                        String currentPrompt = blankToEmpty(data.getSystemPrompt());
                        builder.systemPrompt(currentPrompt + "\n\n" + skillPrompt
                                + SystemPromptSections.SKILL_USAGE_GUIDE);
                        log.debug("已将技能摘要注入系统提示词（按需加载模式）");
                    }
                } else {
                    // 全量注入模式：将完整技能内容拼入 sysPrompt
                    injectFullSkillContent(builder, data, skillBox);
                }
            }
        }

        log.debug("工具链装配完成: toolkit={}, skillBox={}, onDemand={}, hookInject={}, dynamicRebuild={}",
                data.getToolkit() != null ? "已配置" : "无",
                data.getSkillBox() != null ? "已配置" : "无",
                data.isSkillLoadOnDemand(),
                data.isSkillHookInject(),
                data.isSkillDynamicRebuild());
    }

    /**
     * 全量注入技能内容到系统提示词
     * @param builder
     * @param data
     * @param skillBox
     */
    @SuppressWarnings("deprecation")
    private void injectFullSkillContent(AgentRuntimeBuilder builder, AgentBootstrap data, AgentSkillBox skillBox) {
        String skillPrompt = skillBox.buildSystemPrompt();
        if (skillPrompt != null && !skillPrompt.isBlank()) {
            String currentPrompt = blankToEmpty(data.getSystemPrompt());
            builder.systemPrompt(currentPrompt + "\n\n" + skillPrompt);
            log.debug("已将技能内容注入系统提示词");
        }
    }

    /**
     * @param builder
     * @param data
     */
    private void configureMemoryAndIters(AdvancedAgentRuntimeBuilder builder, AgentBootstrap data) {
        builder.memoryConfig(AgentMemoryConfig.defaults());
        builder.maxIters(data.getMaxIters());
        registerLongTermMemoryTools(data);
    }

    /**
     * 将长期记忆工具注册到当前 Toolkit，受禁用钩子与配置开关双重控制
     * @param data
     */
    private void registerLongTermMemoryTools(AgentBootstrap data) {
        if (disableLongTermMemoryTools() || data.isDisableLongTermMemoryTools()) {
            log.debug("长期记忆工具已被禁用");
            return;
        }
        if (longTermMemoryToolRegistrar == null || !longTermMemoryToolRegistrar.isToolEnabled()) {
            return;
        }
        AgentToolkit toolkit = data.getToolkit();
        if (toolkit == null) {
            return;
        }
        longTermMemoryToolRegistrar.registerTo(toolkit);
    }

    /**
     * 是否禁用长期记忆工具，子类可覆盖返回 true 以禁用
     * @return
     */
    protected boolean disableLongTermMemoryTools() {
        return false;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
