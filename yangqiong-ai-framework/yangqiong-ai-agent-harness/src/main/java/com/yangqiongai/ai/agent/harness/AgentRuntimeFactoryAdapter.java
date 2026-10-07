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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.core.AdvancedAgentRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.AgentRuntimeFactory;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.config.AgentMemoryConfig;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent运行时工厂适配器
 * @author yangqiong
 */
public class AgentRuntimeFactoryAdapter implements AgentRuntimeFactory {

    /**
     * 框架运行时工厂委托
     */
    private final com.yangqiongai.ai.agent.runtime.AgentRuntimeFactory delegate;

    public AgentRuntimeFactoryAdapter(com.yangqiongai.ai.agent.runtime.AgentRuntimeFactory delegate) {
        this.delegate = delegate;
    }

    /**
     * 创建运行时构建器
     * @return
     */
    @Override
    public AdvancedAgentRuntimeBuilder createBuilder() {
        com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder runtimeBuilder = delegate.createBuilder();
        return runtimeBuilder == null ? null : new BuilderAdapter(runtimeBuilder);
    }

    /**
     * 运行时构建器适配器
     * @author yangqiong
     */
    private static final class BuilderAdapter implements AdvancedAgentRuntimeBuilder {

        /**
         * 框架构建器委托
         */
        private final com.yangqiongai.ai.agent.runtime.AdvancedAgentRuntimeBuilder delegate;

        BuilderAdapter(com.yangqiongai.ai.agent.runtime.AdvancedAgentRuntimeBuilder delegate) {
            this.delegate = delegate;
        }

        /**
         * 设置Agent名称
         * @param name
         * @return
         */
        @Override
        public BuilderAdapter name(String name) {
            delegate.name(name);
            return this;
        }

        /**
         * 设置模型
         * @param model
         * @return
         */
        @Override
        public BuilderAdapter model(AgentModel model) {
            delegate.model(unwrapModel(model));
            return this;
        }

        /**
         * 设置系统提示词
         * @param systemPrompt
         * @return
         */
        @Override
        public BuilderAdapter systemPrompt(String systemPrompt) {
            delegate.systemPrompt(systemPrompt);
            return this;
        }

        /**
         * 设置最大迭代次数
         * @param maxIters
         * @return
         */
        @Override
        public BuilderAdapter maxIters(int maxIters) {
            delegate.maxIters(maxIters);
            return this;
        }

        /**
         * 设置工具箱
         * @param toolkit
         * @return
         */
        @Override
        public BuilderAdapter toolkit(AgentToolkit toolkit) {
            if (toolkit == null) {
                delegate.toolkit(null);
                return this;
            }
            if (toolkit instanceof AgentToolkitAdapter a) {
                delegate.toolkit(a.getDelegate());
                return this;
            }
            throw new UnsupportedOperationException("仅支持由AgentToolkitAdapter包装的框架工具箱");
        }

        /**
         * 设置中间件
         * @param middleware
         * @return
         */
        @Override
        public BuilderAdapter middleware(AgentMiddleware middleware) {
            if (middleware == null) {
                delegate.middleware(null);
                return this;
            }
            if (middleware instanceof AgentMiddlewareAdapter a) {
                delegate.middleware(a.getDelegate());
                return this;
            }
            throw new UnsupportedOperationException("仅支持由AgentMiddlewareAdapter包装的框架中间件");
        }

        /**
         * 设置模型生成选项
         * @param options
         * @return
         */
        @Override
        public BuilderAdapter generateOptions(AgentGenerateOptions options) {
            delegate.generateOptions(SpiConverters.toRuntimeOptions(options));
            return this;
        }

        /**
         * 设置技能箱
         * @param skillBox
         * @return
         */
        @Override
        public BuilderAdapter skillBox(AgentSkillBox skillBox) {
            if (skillBox == null) {
                delegate.skillBox(null);
                return this;
            }
            if (skillBox instanceof AgentSkillBoxAdapter a) {
                delegate.skillBox(a.getDelegate());
                return this;
            }
            throw new UnsupportedOperationException("仅支持由AgentSkillBoxAdapter包装的框架技能箱");
        }

        /**
         * 设置响应格式
         * @param format
         * @return
         */
        @Override
        public BuilderAdapter responseFormat(AgentResponseFormat format) {
            delegate.responseFormat(SpiConverters.toRuntimeResponseFormat(format));
            return this;
        }

        /**
         * 设置结构化输出类型
         * @param type
         * @return
         */
        @Override
        public BuilderAdapter structuredOutputType(Class<?> type) {
            delegate.structuredOutputType(type);
            return this;
        }

        /**
         * 设置权限模式与规则
         * @param mode
         * @param rule
         * @return
         */
        @Override
        public BuilderAdapter permission(AgentPermissionMode mode, AgentPermissionRule rule) {
            delegate.permission(
                    SpiConverters.convertEnum(mode, com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode.class),
                    SpiConverters.toRuntimePermissionRule(rule));
            return this;
        }

        /**
         * 设置权限上下文状态
         * @param state
         * @return
         */
        @Override
        public BuilderAdapter permissionContextState(AgentPermissionContextState state) {
            delegate.permissionContextState(SpiConverters.toRuntimePermissionContextState(state));
            return this;
        }

        /**
         * 设置记忆配置
         * @param config
         * @return
         */
        @Override
        public BuilderAdapter memoryConfig(AgentMemoryConfig config) {
            delegate.memoryConfig(SpiConverters.toRuntimeMemoryConfig(config));
            return this;
        }

        /**
         * 设置压缩配置
         * @param config
         * @return
         */
        @Override
        public BuilderAdapter compactionConfig(AgentCompactionConfig config) {
            delegate.compactionConfig(SpiConverters.toRuntimeCompactionConfig(config));
            return this;
        }

        /**
         * 设置工具结果驱逐配置
         * @param config
         * @return
         */
        @Override
        public BuilderAdapter toolResultEvictionConfig(AgentToolResultEvictionConfig config) {
            delegate.toolResultEvictionConfig(SpiConverters.toRuntimeEvictionConfig(config));
            return this;
        }

        /**
         * 设置工具选择策略
         * @param choice
         * @return
         */
        @Override
        public BuilderAdapter toolChoice(AgentToolChoice choice) {
            delegate.toolChoice(SpiConverters.convertEnum(choice, com.yangqiongai.ai.agent.runtime.config.AgentToolChoice.class));
            return this;
        }

        /**
         * 构建Agent运行时
         * @return
         */
        @Override
        public AgentRuntime build() {
            com.yangqiongai.ai.agent.runtime.AgentRuntime runtime = delegate.build();
            return runtime == null ? null : new RuntimeAdapter(runtime);
        }

        /**
         * 解包独立模型为框架模型
         * @param model
         * @return
         */
        private static com.yangqiongai.ai.agent.runtime.model.AgentModel unwrapModel(AgentModel model) {
            if (model == null) {
                return null;
            }
            if (model instanceof AgentModelAdapter a) {
                return a.getDelegate();
            }
            throw new UnsupportedOperationException("仅支持由AgentModelAdapter包装的框架模型");
        }
    }

    /**
     * 运行时适配器
     * @author yangqiong
     */
    private static final class RuntimeAdapter implements AgentRuntime {

        /**
         * 框架运行时委托
         */
        private final com.yangqiongai.ai.agent.runtime.AgentRuntime delegate;

        RuntimeAdapter(com.yangqiongai.ai.agent.runtime.AgentRuntime delegate) {
            this.delegate = delegate;
        }

        /**
         * 同步调用Agent
         * @param inputs
         * @param context
         * @return
         */
        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return delegate.call(SpiConverters.toRuntimeMessages(inputs), SpiConverters.toRuntimeContext(context))
                    .map(SpiConverters::toHarnessMessage);
        }

        /**
         * 流式输出Agent事件
         * @param inputs
         * @param context
         * @return
         */
        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return delegate.streamEvents(SpiConverters.toRuntimeMessages(inputs), SpiConverters.toRuntimeContext(context))
                    .mapNotNull(SpiConverters::toHarnessEvent);
        }

        /**
         * 获取Agent名称
         * @return
         */
        @Override
        public String getName() {
            return delegate.getName();
        }

        /**
         * 恢复因人工确认暂停的Agent执行
         * @param confirmResults
         * @param context
         * @return
         */
        @Override
        public Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, AgentRuntimeContext context) {
            List<com.yangqiongai.ai.agent.runtime.event.ConfirmResult> runtimeResults = new ArrayList<>();
            if (confirmResults != null) {
                for (ConfirmResult result : confirmResults) {
                    runtimeResults.add(SpiConverters.toRuntimeConfirmResult(result));
                }
            }
            return delegate.resume(runtimeResults, SpiConverters.toRuntimeContext(context))
                    .mapNotNull(SpiConverters::toHarnessEvent);
        }

        /**
         * 恢复因澄清请求暂停的Agent执行
         * @param answers
         * @param context
         * @return
         */
        @Override
        public Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, AgentRuntimeContext context) {
            List<com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer> runtimeAnswers = new ArrayList<>();
            if (answers != null) {
                for (ClarificationAnswer answer : answers) {
                    runtimeAnswers.add(new com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer(
                            answer.toolCallId(), answer.answer()));
                }
            }
            return delegate.resumeWithClarification(runtimeAnswers, SpiConverters.toRuntimeContext(context))
                    .mapNotNull(SpiConverters::toHarnessEvent);
        }
    }
}
