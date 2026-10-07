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
package com.yangqiongai.ai.agent.rag.annotation;

import java.lang.annotation.*;

/**
 * 知识库RAG注入
 * <p>
 * 标注在 AgentProcessor 的 createAgentContext(AgentRequest) 方法上，由 KnowledgeRagAspect 切面拦截，
 * 在方法执行前根据请求输入检索知识库，将知识上下文写入 request.body。
 * 随后 AbstractAgentProcessor.enrichInputsWithKnowledge 会读取并拼接到输入消息头部。
 * </p>
 * <p>
 * 使用示例：在依赖 ai-agent-rag 模块的 Processor 子类中覆盖 createAgentContext 并标注本注解：
 * <pre>
 * &#064;Override
 * &#064;KnowledgeRag(kbIds = {"kb-001", "kb-002"}, topK = 5)
 * public AgentContext createAgentContext(AgentRequest request) {
 *     return super.createAgentContext(request);
 * }
 * </pre>
 * </p>
 * <p>
 * 与 ContextAwareRagTool 的区别：本注解为声明式预注入（构建期注入），
 * ContextAwareRagTool 为 LLM 自主调用工具（运行期按需检索），两者可并存。
 * kbIds 为空时跳过注入。
 * </p>
 * @author yangqiong
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KnowledgeRag {

    /**
     * 知识库ID列表
     */
    String[] kbIds() default {};

    /**
     * TopK
     */
    int topK() default 5;
}
