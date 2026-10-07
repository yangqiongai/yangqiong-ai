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
package com.yangqiongai.ai.agent.rag.aspect;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.KnowledgeEvidenceBodies;
import com.yangqiongai.ai.agent.rag.annotation.KnowledgeRag;
import com.yangqiongai.ai.agent.rag.provider.KnowledgeContextFormatter;
import com.yangqiongai.ai.agent.rag.provider.RagKnowledgeSupport;
import com.yangqiongai.ai.agent.rag.resolver.KnowledgeResolver;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库RAG切面
 * @author yangqiong
 */
@Aspect
@Component
public class KnowledgeRagAspect {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRagAspect.class);

    private final ObjectProvider<KnowledgeResolver> knowledgeResolverProvider;

    public KnowledgeRagAspect(ObjectProvider<KnowledgeResolver> knowledgeResolverProvider) {
        this.knowledgeResolverProvider = knowledgeResolverProvider;
    }

    /**
     * 拦截标注了@KnowledgeRag的方法，注入知识上下文
     * <p>
     * 支持两种参数场景：
     * 1. 方法参数含 AgentContext（如 execute）：直接设置属性
     * 2. 方法参数含 AgentRequest（如 createAgentContext）：写入 request.body，后续 createAgentContext 可读取
     * </p>
     * @param joinPoint
     * @param knowledgeRag
     * @return
     * @throws Throwable
     */
    @Around("@annotation(knowledgeRag)")
    public Object aroundKnowledgeRag(ProceedingJoinPoint joinPoint, KnowledgeRag knowledgeRag) throws Throwable {
        KnowledgeResolver resolver = knowledgeResolverProvider.getIfAvailable();
        if (resolver == null) {
            log.warn("KnowledgeResolver不可用, 跳过知识上下文注入");
            return joinPoint.proceed();
        }

        List<String> kbIds = Arrays.asList(knowledgeRag.kbIds());
        int topK = knowledgeRag.topK();

        AgentContext agentContext = findAgentContext(joinPoint.getArgs());
        AgentRequest agentRequest = findAgentRequest(joinPoint.getArgs());

        String query = resolveQuery(agentContext, agentRequest);
        if (query == null || query.isBlank() || kbIds.isEmpty()) {
            return joinPoint.proceed();
        }

        List<RetrievalEvidence> knowledgeResults = resolver.resolve(query, kbIds, topK);
        String knowledgeContext = KnowledgeContextFormatter.format(knowledgeResults);

        if (agentContext != null) {
            agentContext.setAttribute(AgentContext.CTX_KNOWLEDGE_CONTEXT, knowledgeContext);
            // 命中证据写入请求body（执行完成后随结果finalPayload透出）
            if (agentContext.getRequest() != null) {
                KnowledgeEvidenceBodies.mergeTo(agentContext.getRequest().getBody(), toEvidenceMaps(knowledgeResults));
            }
        } else if (agentRequest != null) {
            agentRequest.addBody(AgentRequest.BodyKeys.KNOWLEDGE_CONTEXT, knowledgeContext);
            KnowledgeEvidenceBodies.mergeTo(agentRequest.getBody(), toEvidenceMaps(knowledgeResults));
        } else {
            log.warn("未找到AgentContext或AgentRequest参数, 跳过知识上下文注入");
            return joinPoint.proceed();
        }

        log.info("知识上下文注入完成: query={}, kbIds={}, resultCount={}, contextLength={}",
                query, kbIds, knowledgeResults.size(), knowledgeContext.length());

        return joinPoint.proceed();
    }

    /**
     * 从方法参数中查找AgentContext
     * @param args
     * @return
     */
    private AgentContext findAgentContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AgentContext) {
                return (AgentContext) arg;
            }
        }
        return null;
    }

    /**
     * 从方法参数中查找AgentRequest
     * @param args
     * @return
     */
    private AgentRequest findAgentRequest(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof AgentRequest) {
                return (AgentRequest) arg;
            }
        }
        return null;
    }

    /**
     * 从AgentContext或AgentRequest中提取查询文本
     * @param agentContext
     * @param agentRequest
     * @return
     */
    private String resolveQuery(AgentContext agentContext, AgentRequest agentRequest) {
        if (agentContext != null && agentContext.getRequest() != null) {
            return agentContext.getRequest().getInputAsText();
        }
        if (agentRequest != null) {
            return agentRequest.getInputAsText();
        }
        return null;
    }

    /**
     * 检索证据转Map列表（供body承载与结果透出）
     * @param results
     * @return
     */
    private List<Map<String, Object>> toEvidenceMaps(List<RetrievalEvidence> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(RagKnowledgeSupport::toEvidenceMap)
                .collect(Collectors.toList());
    }
}
