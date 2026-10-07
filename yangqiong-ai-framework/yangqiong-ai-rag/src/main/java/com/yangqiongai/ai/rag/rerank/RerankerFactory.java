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
package com.yangqiongai.ai.rag.rerank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rerank策略工厂
 * @author yangqiong
 */
@Component
public class RerankerFactory {

    private static final Logger log = LoggerFactory.getLogger(RerankerFactory.class);

    private static final String DEFAULT_STRATEGY = "llm";

    @Value("${ai.rag.rerank.strategy:llm}")
    private String strategy;

    private final Map<String, Reranker> rerankServiceMap;

    @Autowired
    public RerankerFactory(List<Reranker> rerankers) {
        this.rerankServiceMap = rerankers == null ? Collections.emptyMap()
                : rerankers.stream()
                        .collect(Collectors.toMap(
                                service -> service.getStrategy().toLowerCase(),
                                service -> service,
                                (a, b) -> a));
    }

    /**
     * 获取Rerank服务实例
     * @return
     */
    public Reranker getRerankService() {
        String key = strategy.toLowerCase();
        Reranker service = rerankServiceMap.get(key);
        if (service != null) {
            return service;
        }
        log.warn("未知的Rerank策略: {}, 降级为{}", strategy, DEFAULT_STRATEGY);
        return rerankServiceMap.get(DEFAULT_STRATEGY);
    }
}
