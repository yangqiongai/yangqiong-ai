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
package com.yangqiongai.ai.data.rag.listener;

import com.yangqiongai.ai.common.event.KnowledgeBasePurgeEvent;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.storage.VectorStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 知识库物理删除事件监听器
 * @author yangqiong
 */
@Component
public class KnowledgeBasePurgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBasePurgeEventListener.class);

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    private VectorStorageService qdrantVectorService;

    @Autowired
    private CollectionNameResolver collectionNameResolver;

    /**
     * 知识库物理删除时清理关联切片和向量
     * @param event
     */
    @Async("ragExecutor")
    @EventListener
    public void onKnowledgeBasePurge(KnowledgeBasePurgeEvent event) {
        String kbId = event.getKbId();
        log.info("收到知识库物理删除事件, 开始清理切片和向量, kbId: {}", kbId);

        int sliceCount = sliceRecordRepository.deleteByKbId(kbId);
        log.info("知识库切片清理完成, kbId: {}, 删除切片数: {}", kbId, sliceCount);

        cleanQdrantCollection(kbId);
    }

    /**
     * 清理 Qdrant 中知识库对应的整个集合
     * @param kbId
     */
    private void cleanQdrantCollection(String kbId) {
        try {
            String collectionName = collectionNameResolver.resolve(kbId);
            qdrantVectorService.deleteCollection(collectionName);
            log.info("知识库向量集合清理完成, kbId: {}, collection: {}", kbId, collectionName);
        } catch (Exception e) {
            log.warn("清理 Qdrant 集合异常, kbId: {}", kbId, e);
        }
    }
}
