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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认检索路径解析器
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.rag.route.strategy", havingValue = "default", matchIfMissing = true)
public class DefaultRetrievalPathResolver implements RetrievalPathResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetrievalPathResolver.class);

    /**
     * 决定检索路径，默认返回HYBRID
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
        log.debug("默认路由决策: query={}, kbCount={}, route=HYBRID",
                query, kbIds.size());
        return RetrievalRoute.HYBRID;
    }
}
