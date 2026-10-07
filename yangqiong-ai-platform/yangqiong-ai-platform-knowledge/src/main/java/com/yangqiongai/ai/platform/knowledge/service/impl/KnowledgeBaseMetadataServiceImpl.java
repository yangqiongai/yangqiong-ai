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
package com.yangqiongai.ai.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yangqiongai.ai.common.rag.KnowledgeBaseMetadataHandler;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 知识库元数据端口实现
 * @author yangqiong
 */
@Service
public class KnowledgeBaseMetadataServiceImpl implements KnowledgeBaseMetadataHandler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseMetadataServiceImpl.class);

    /**
     * 知识库默认活跃版本号
     */
    private static final String DEFAULT_VERSION = "v1";

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public String resolveActiveVersion(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return null;
        }
        QueryWrapper<KnowledgeBase> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId).select("active_version");
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        return kb != null ? kb.getActiveVersion() : null;
    }

    /**
     * 确保知识库存在活跃版本（为空时初始化为默认版本v1并回写），返回最终版本号
     * @param kbId
     * @return 知识库不存在时返回null
     */
    public String ensureActiveVersion(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return null;
        }
        QueryWrapper<KnowledgeBase> wrapper = new QueryWrapper<>();
        wrapper.eq("kb_id", kbId);
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        if (kb == null) {
            return null;
        }
        if (kb.getActiveVersion() != null && !kb.getActiveVersion().isBlank()) {
            return kb.getActiveVersion();
        }
        kb.setActiveVersion(DEFAULT_VERSION);
        knowledgeBaseMapper.updateById(kb);
        log.info("知识库活跃版本为空, 已初始化为{}, kbId: {}", DEFAULT_VERSION, kbId);
        return kb.getActiveVersion();
    }
}
