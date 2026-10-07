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
package com.yangqiongai.ai.rag.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agentic智能路由
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.rag.route.strategy", havingValue = "agentic")
public class AgenticRetrievalPathResolver implements RetrievalPathResolver {

    private static final Logger log = LoggerFactory.getLogger(AgenticRetrievalPathResolver.class);

    @Autowired
    private QueryIntentionRecognizer intentionRecognizer;

    /**
     * 图谱模块总开关
     */
    @Value("${ai.graph.enabled:false}")
    private boolean graphEnabled;

    /**
     * 图谱路由开关
     */
    @Value("${ai.graph.integration.graph-route-enabled:true}")
    private boolean graphRouteEnabled;

    /**
     * 决定检索路径
     * @param query
     * @param kbIds
     * @return
     */
    @Override
    public RetrievalRoute decide(String query, List<String> kbIds) {
        if (query == null || query.isBlank()) {
            log.debug("查询为空，跳过检索");
            return RetrievalRoute.SKIP;
        }
        if (kbIds == null || kbIds.isEmpty()) {
            log.debug("知识库ID为空，跳过检索");
            return RetrievalRoute.SKIP;
        }

        QueryIntention intention = intentionRecognizer.recognize(query);
        RetrievalRoute route = mapIntentionToRoute(intention);

        log.info("Agentic路由决策: query={}, intention={}, route={}", query, intention, route);
        return route;
    }

    /**
     * 意图到路由的映射。WEB_SEARCH 路由不在此处主动判定，而是由 RetrievalAgent
     * 反思闭环（ContextGrader 评估证据不足且涉及时效性时）动态升级触发。
     * RELATIONAL/COMPLEX 在图谱双开关开启时路由到 GRAPH，任一关闭降级到原策略。
     * @param intention
     * @return
     */
    private RetrievalRoute mapIntentionToRoute(QueryIntention intention) {
        boolean graphAvailable = graphEnabled && graphRouteEnabled;
        switch (intention) {
            case FACTUAL:
                // 事实型查询：向量检索擅长语义精准匹配
                return RetrievalRoute.VECTOR_ONLY;
            case RELATIONAL:
                // 关系型查询：图谱擅长实体关系遍历，降级为全文检索
                if (graphAvailable) {
                    return RetrievalRoute.GRAPH;
                }
                return RetrievalRoute.FULLTEXT_ONLY;
            case ANALYTICAL:
                // 分析型查询：混合检索提供多维度覆盖
                return RetrievalRoute.HYBRID;
            case COMPLEX:
                // 复杂查询：图谱擅长多跳推理，降级为混合检索
                if (graphAvailable) {
                    return RetrievalRoute.GRAPH;
                }
                return RetrievalRoute.HYBRID;
            case UNKNOWN:
            default:
                // 未知类型降级为混合检索
                return RetrievalRoute.HYBRID;
        }
    }
}
