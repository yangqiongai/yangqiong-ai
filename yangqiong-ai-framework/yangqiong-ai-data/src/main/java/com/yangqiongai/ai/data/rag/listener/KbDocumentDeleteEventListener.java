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

import com.yangqiongai.ai.common.event.KbDocumentDeleteEvent;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.storage.VectorStorageService;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文档删除事件监听器
 * @author yangqiong
 */
@Component
public class KbDocumentDeleteEventListener {

    private static final Logger log = LoggerFactory.getLogger(KbDocumentDeleteEventListener.class);

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    private VectorStorageService qdrantVectorService;

    @Autowired
    private CollectionNameResolver collectionNameResolver;

    /**
     * 文档删除时清理关联切片和向量
     * @param event
     */
    @Async("ragExecutor")
    @EventListener
    public void onDocumentDelete(KbDocumentDeleteEvent event) {
        String docId = event.getDocId();
        String kbId = event.getKbId();
        log.info("收到文档删除事件, 开始清理切片和向量, docId: {}, kbId: {}", docId, kbId);

        int sliceCount = sliceRecordRepository.deleteByKbIdAndDocId(kbId, docId);
        log.info("文档切片清理完成, docId: {}, 删除切片数: {}", docId, sliceCount);

        cleanQdrantVectors(docId, kbId);
    }

    /**
     * 清理 Qdrant 中文档关联的向量点
     * @param docId
     * @param kbId
     */
    private void cleanQdrantVectors(String docId, String kbId) {
        try {
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("docId")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(docId)
                                            .build())
                                    .build())
                            .build())
                    .build();
            qdrantVectorService.deleteByFilter(collectionNameResolver.resolve(kbId), filter);
            log.info("文档向量清理完成, docId: {}, kbId: {}", docId, kbId);
        } catch (Exception e) {
            log.warn("清理 Qdrant 向量异常, docId: {}, kbId: {}", docId, kbId, e);
        }
    }
}
