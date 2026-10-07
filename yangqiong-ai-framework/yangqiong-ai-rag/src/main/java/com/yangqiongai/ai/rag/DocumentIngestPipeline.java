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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.common.rag.IngestDocument;

/**
 * 文档入库流水线
 * @author yangqiong
 */
public interface DocumentIngestPipeline {

    /**
     * 按文档ID执行入库流水线（从端口加载文档元数据）
     * @param docId
     */
    void process(String docId);

    /**
     * 直接处理入库文档对象（支持文本类数据源，无需端口加载）
     * @param document
     */
    void process(IngestDocument document);

    /**
     * 更新状态
     * @param docId
     * @param status
     * @param chunkCount
     * @param errorMessage
     */
    void updateStatusSafely(String docId, DocumentStatus status, Integer chunkCount, String errorMessage) ;
}
