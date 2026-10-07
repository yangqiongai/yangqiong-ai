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
package com.yangqiongai.ai.rag.query;

import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagCacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 查询改写
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.rag.query-rewrite.enabled", havingValue = "true")
public class DefaultQueryRewriter implements QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(DefaultQueryRewriter.class);

    private static final String REWRITE_SYSTEM_INSTRUCTION =
            "你是一个专业的查询改写助手。请对用户的查询进行改写，使其更适合文档检索。" +
            "要求：保持核心意图不变；补充缺失的上下文信息；展开缩写为完整术语；" +
            "使用更精确的专业术语；保持原始语言；只输出改写后的查询，不要添加任何说明。";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.rag.query-rewrite.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 改写查询（结果走缓存）
     * @param query
     * @return
     */
    @Override
    @Cacheable(value = RagCacheConfiguration.CACHE_QUERY_REWRITE, key = "#query", unless = "#result == null || #result == #query")
    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入, 跳过查询改写");
            return query;
        }

        try {
            String rewritten = languageModelFactory.generateText(modelCode, REWRITE_SYSTEM_INSTRUCTION, query);
            if (rewritten != null && !rewritten.isBlank()) {
                log.debug("查询改写: [{}] -> [{}]", query, rewritten);
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.warn("查询改写失败, 使用原始查询: {}", query, e);
        }
        return query;
    }
}
