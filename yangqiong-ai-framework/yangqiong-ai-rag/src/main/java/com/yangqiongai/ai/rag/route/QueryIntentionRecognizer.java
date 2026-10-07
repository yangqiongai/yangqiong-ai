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

import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagCacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 查询意图识别（支持缓存）
 * @author yangqiong
 */
@Service
public class QueryIntentionRecognizer {

    private static final Logger log = LoggerFactory.getLogger(QueryIntentionRecognizer.class);

    private static final String INTENTION_SYSTEM_INSTRUCTION =
            "你是一个查询意图分类助手。请判断用户查询属于以下哪种意图，只返回意图名称（大写英文），不要添加任何其他内容。\n" +
            "意图类型：\n" +
            "FACTUAL - 事实型查询，询问定义、概念、属性等具体事实（如：xxx是什么、xxx的定义）\n" +
            "RELATIONAL - 关系型查询，询问实体间关系、包含、列举等（如：A和B的关系、xxx包含哪些）\n" +
            "ANALYTICAL - 分析型查询，询问原因、方法、对比、推理等（如：为什么、如何、对比分析）\n" +
            "COMPLEX - 复杂查询，包含多个条件、多实体组合、嵌套问题\n" +
            "UNKNOWN - 无法判断意图";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.rag.route.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 识别查询意图（结果走缓存）
     * @param query
     * @return
     */
    @Cacheable(value = RagCacheConfiguration.CACHE_INTENTION, key = "#query", unless = "#result == T(com.yangqiongai.ai.rag.route.QueryIntention).UNKNOWN")
    public QueryIntention recognize(String query) {
        if (query == null || query.isBlank()) {
            return QueryIntention.UNKNOWN;
        }
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入, 降级为UNKNOWN意图");
            return QueryIntention.UNKNOWN;
        }

        try {
            String response = languageModelFactory.generateText(modelCode, INTENTION_SYSTEM_INSTRUCTION, query);
            return parseIntention(response);
        } catch (Exception e) {
            log.warn("意图识别失败, 降级为UNKNOWN意图: {}", query, e);
            return QueryIntention.UNKNOWN;
        }
    }

    /**
     * 解析LLM返回的意图
     * @param response
     * @return
     */
    private QueryIntention parseIntention(String response) {
        if (response == null || response.isBlank()) {
            return QueryIntention.UNKNOWN;
        }
        String trimmed = response.trim().toUpperCase(Locale.ROOT);
        for (QueryIntention intention : QueryIntention.values()) {
            if (trimmed.contains(intention.name())) {
                return intention;
            }
        }
        return QueryIntention.UNKNOWN;
    }
}
