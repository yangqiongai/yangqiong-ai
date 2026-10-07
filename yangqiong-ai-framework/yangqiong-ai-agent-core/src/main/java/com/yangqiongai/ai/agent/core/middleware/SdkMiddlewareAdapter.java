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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.core.orchestration.SubagentDeclaration;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * SDK中间件适配器，封装框架层AgentRuntimeBuilder的注册逻辑
 * <p>
 * 通过反射调用HarnessRuntimeBuilder的扩展方法，避免ai-agent-core对ai-agent-harness的循环依赖。
 * </p>
 * @author yangqiong
 */
public class SdkMiddlewareAdapter {

    private static final Logger log = LoggerFactory.getLogger(SdkMiddlewareAdapter.class);

    private static final String HARNESS_BUILDER_CLASS = "com.yangqiongai.ai.agent.harness.spi.HarnessRuntimeBuilder";

    /**
     * 将子代理声明列表注册到Builder中
     * @param builder
     * @param declarations
     */
    public static void registerSubagentDeclarations(AgentRuntimeBuilder builder,
                                                     List<SubagentDeclaration> declarations) {
        if (builder == null || declarations == null || declarations.isEmpty()) {
            return;
        }
        try {
            Class<?> harnessBuilderClass = Class.forName(HARNESS_BUILDER_CLASS);
            if (harnessBuilderClass.isInstance(builder)) {
                Method method = harnessBuilderClass.getMethod("subagentDeclarations", List.class);
                method.invoke(builder, declarations);
                for (SubagentDeclaration decl : declarations) {
                    log.debug("注册子代理声明到HarnessBuilder: name={}", decl.getName());
                }
                return;
            }
        } catch (ClassNotFoundException e) {
            log.debug("ai-agent-harness未加载，跳过子代理声明注册");
        } catch (Exception e) {
            log.warn("子代理声明注册失败: builderType={}", builder.getClass().getName(), e);
        }
        log.warn("当前Builder类型不支持子代理声明注册: builderType={}", builder.getClass().getName());
    }

    /**
     * 启用规划模式
     * @param builder
     */
    public static void enablePlanMode(AgentRuntimeBuilder builder) {
        if (builder == null) {
            return;
        }
        try {
            Class<?> harnessBuilderClass = Class.forName(HARNESS_BUILDER_CLASS);
            if (harnessBuilderClass.isInstance(builder)) {
                Method method = harnessBuilderClass.getMethod("enablePlanMode");
                method.invoke(builder);
                log.debug("HarnessBuilder计划模式已启用");
                return;
            }
        } catch (ClassNotFoundException e) {
            log.debug("ai-agent-harness未加载，跳过计划模式启用");
        } catch (Exception e) {
            log.warn("计划模式启用失败: builderType={}", builder.getClass().getName(), e);
        }
        log.warn("当前Builder类型不支持计划模式: builderType={}", builder.getClass().getName());
    }
}
