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

import com.yangqiongai.ai.common.event.DocumentIngestEvent;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.rag.DocumentIngestPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档入库事件监听器
 * @author yangqiong
 */
@Component
public class KbDocumentIngestEventListener {

    private static final Logger log = LoggerFactory.getLogger(KbDocumentIngestEventListener.class);

    @Autowired
    private DocumentIngestPipeline pipeline;

    /**
     * 异步处理文档入库事件
     * @param event
     */
    @Async("ragExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIngestEvent(DocumentIngestEvent event) {
        try {
            pipeline.process(event.getDocId());
        } catch (Exception e) {
            log.error("文档入库流水线执行失败, docId: {}", event.getDocId(), e);
            pipeline.updateStatusSafely(event.getDocId(), DocumentStatus.FAILED, null, e.getMessage());

        }
    }
}
