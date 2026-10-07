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
package com.yangqiongai.ai.rag.config;

import com.yangqiongai.ai.common.rag.KnowledgeBaseMetadataHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 知识库版本解析（支持缓存）
 * @author yangqiong
 */
@Service
public class KnowledgeBaseVersionResolver {

    @Autowired
    private KnowledgeBaseMetadataHandler knowledgeBaseMetadataHandler;

    /**
     * 获取知识库当前活跃版本号
     * @param kbId
     * @return
     */
    @Cacheable(value = RagCacheConfiguration.CACHE_ACTIVE_VERSION, key = "#kbId", unless = "#result == null")
    public String resolveActiveVersion(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return null;
        }
        return knowledgeBaseMetadataHandler.resolveActiveVersion(kbId);
    }
}
